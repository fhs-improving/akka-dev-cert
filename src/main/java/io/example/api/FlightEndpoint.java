package io.example.api;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.example.application.BookingSlotEntity;
import io.example.application.ParticipantSlotsView;
import io.example.domain.Participant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import akka.javasdk.CommandException;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import io.example.application.ParticipantSlotsView.SlotList;
import io.example.domain.Participant.ParticipantType;
import io.example.domain.Timeslot;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/flight")
public class FlightEndpoint extends AbstractHttpEndpoint {
    private final Logger log = LoggerFactory.getLogger(FlightEndpoint.class);

    private final ComponentClient componentClient;

    public FlightEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record ExternalTimeslot(List<Timeslot.Booking> bookings, List<Participant> available){}

    // Creates a new booking. All three identified participants will
    // be considered booked for the given timeslot, if they are all
    // "available" at the time of booking.
    @Post("/bookings/{slotId}")
    public HttpResponse createBooking(String slotId, BookingRequest request) {
        try {
            log.info("Creating booking for slot {}: {}", slotId, request);

            BookingSlotEntity.Command.BookReservation bookReservation = new BookingSlotEntity.Command.BookReservation(
                    request.studentId, request.aircraftId, request.instructorId, request.bookingId
            );

            componentClient
                    .forEventSourcedEntity(slotId)
                    .method(BookingSlotEntity::bookSlot)
                    .invoke(bookReservation);

            return HttpResponses.created();
        } catch (CommandException e){
            throw HttpException.badRequest(e.getMessage());
        }
    }

    // Cancels an existing booking. Note that both the slot
    // ID and the booking ID are required.
    @Delete("/bookings/{slotId}/{bookingId}")
    public HttpResponse cancelBooking(String slotId, String bookingId) {
        try {
            log.info("Canceling booking id {}", bookingId);
            componentClient
                    .forEventSourcedEntity(slotId)
                    .method(BookingSlotEntity::cancelBooking)
                    .invoke(bookingId);
            return HttpResponses.ok();
        } catch (CommandException e) {
            throw HttpException.badRequest(e.getMessage());
        }
    }

    // Retrieves all slots in which a given participant has the supplied status.
    // Used to retrieve bookings and slots in which the participant is available
    @Get("/slots/{participantId}/{status}")
    public SlotList slotsByStatus(String participantId, String status) {
        if (!(status.equals(ParticipantSlotsView.SlotStatus.AVAILABLE) || status.equals(ParticipantSlotsView.SlotStatus.BOOKED))) {
                throw HttpException.badRequest("Status was " + status + " but must be one of " + ParticipantSlotsView.SlotStatus.BOOKED + " +and " +
                        ParticipantSlotsView.SlotStatus.AVAILABLE);
        } else {
            ParticipantSlotsView.ParticipantStatusInput participantStatusInput = new ParticipantSlotsView.ParticipantStatusInput(
                    participantId, status
            );

            SlotList slotList = componentClient
                    .forView()
                    .method(ParticipantSlotsView::getSlotsByParticipantAndStatus)
                    .invoke(participantStatusInput);
            List<ParticipantSlotsView.SlotRow> orderedSlots = slotList.slots().stream().sorted(Comparator.comparing(ParticipantSlotsView.SlotRow::slotId)).toList();
            return new SlotList(orderedSlots);
        }
    }

    // Returns the internal availability state for a given slot
    @Get("/availability/{slotId}")
    public Timeslot getSlot(String slotId) {
        return componentClient
                .forEventSourcedEntity(slotId)
                .method(BookingSlotEntity::getSlot)
                .invoke();
    }

    @Get("/availability/public/{slotId}")
    public ExternalTimeslot getSlotForPublic(String slotId) {
        Timeslot timeslot =  componentClient
                .forEventSourcedEntity(slotId)
                .method(BookingSlotEntity::getSlot)
                .invoke();

        List<Timeslot.Booking> externalBookings = timeslot.bookings().stream()
                .sorted(Comparator.comparing(bk -> bk.participant().id()))
                .sorted(Comparator.comparing(Timeslot.Booking::bookingId)).toList();
        List<Participant> externalAvailable = timeslot.available().stream().sorted(Comparator.comparing(Participant::id)).toList();

        return new ExternalTimeslot(externalBookings, externalAvailable);
    }

    // Indicates that the supplied participant is available for booking
    // within the indicated time slot
    @Post("/availability/{slotId}")
    public HttpResponse markAvailable(String slotId, AvailabilityRequest request) {
        ParticipantType participantType;

        try {
            participantType = ParticipantType.valueOf(request.participantType().trim().toUpperCase());
            BookingSlotEntity.Command.MarkSlotAvailable markSlotAvailable = new BookingSlotEntity.Command.MarkSlotAvailable(
                    new Participant(request.participantId, participantType)
            );
            componentClient
                    .forEventSourcedEntity(slotId)
                    .method(BookingSlotEntity::markSlotAvailable)
                    .invoke(markSlotAvailable);
        } catch (CommandException e) {
            throw HttpException.badRequest(e.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("Bad participant type {}", request.participantType());
            throw HttpException.badRequest("invalid participant type");
        }

        log.info("Marking timeslot available for entity {}", slotId);

        // Add entity client to mark slot available

        return HttpResponses.ok();
    }

    // Unmarks a slot as available for the given participant.
    @Delete("/availability/{slotId}")
    public HttpResponse unmarkAvailable(String slotId, AvailabilityRequest request) {
        ParticipantType participantType;
        try {
            participantType = ParticipantType.valueOf(request.participantType().trim().toUpperCase());
            BookingSlotEntity.Command.UnmarkSlotAvailable unmarkSlotAvailable = new BookingSlotEntity.Command.UnmarkSlotAvailable(
                    new Participant(request.participantId, participantType)
            );
            componentClient
                    .forEventSourcedEntity(slotId)
                    .method(BookingSlotEntity::unmarkSlotAvailable)
                    .invoke(unmarkSlotAvailable);
        } catch(CommandException e){
            throw HttpException.badRequest(e.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("Bad participant type {}", request.participantType());
            throw HttpException.badRequest("invalid participant type");
        }

        // Add codce to unmark slot as available

        return HttpResponses.ok();
    }

    // Public API representation of a booking request
    public record BookingRequest(
            String studentId, String aircraftId, String instructorId, String bookingId) {
    }

    // Public API representation of an availability mark/unmark request
    public record AvailabilityRequest(String participantId, String participantType) {
    }
}
