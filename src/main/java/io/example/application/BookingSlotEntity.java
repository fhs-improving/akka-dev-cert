package io.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.example.domain.BookingEvent;
import io.example.domain.Participant;
import io.example.domain.Timeslot;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("booking-slot")
public class BookingSlotEntity extends EventSourcedEntity<Timeslot, BookingEvent> {

    private final String entityId;
    private static final Logger logger = LoggerFactory.getLogger(BookingSlotEntity.class);

    public BookingSlotEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    public Effect<Done> markSlotAvailable(Command.MarkSlotAvailable cmd) {
        boolean alreadyBooked = currentState().bookings().stream().anyMatch(bk -> bk.participant().equals(cmd.participant));
        if (alreadyBooked) {
            return effects().error("Participant " + cmd.participant.id() + " already booked for this slot. To mark the participant available, please cancel the booking first.");
        } else {
            BookingEvent.ParticipantMarkedAvailable event = new BookingEvent.ParticipantMarkedAvailable(
                    entityId, cmd.participant.id(), cmd.participant.participantType()
            );
            return effects().persist(event).thenReply(newState -> Done.done());
        }

    }

    public Effect<Done> unmarkSlotAvailable(Command.UnmarkSlotAvailable cmd) {
        boolean alreadyBooked = currentState().bookings().stream().anyMatch(bk -> bk.participant().equals(cmd.participant));
        if (alreadyBooked) {
            return effects().error("Participant " + cmd.participant.id() + " currently booked for this slot. To mark the participant unavailable, cancel the booking.");
        } else {
            BookingEvent.ParticipantUnmarkedAvailable event = new BookingEvent.ParticipantUnmarkedAvailable(
                    entityId, cmd.participant.id(), cmd.participant.participantType()
            );
            return effects().persist(event).thenReply(newState -> Done.done());
        }

    }

    // NOTE: booking a slot should produce 3
    // `ParticipantBooked` events
    public Effect<Done> bookSlot(Command.BookReservation cmd) {
        if (!currentState().isBookable(cmd.studentId, cmd.aircraftId, cmd.instructorId)) {
            return effects().error("Cannot book slot: one or more participants is unavailable.");
        } else if(!currentState().findBooking(cmd.bookingId).isEmpty()) {
            return effects().error("Cannot book slot: booking id already in use");
        } else {
            BookingEvent.ParticipantBooked studentBooked = new BookingEvent.ParticipantBooked(entityId, cmd.studentId,
                    Participant.ParticipantType.STUDENT, cmd.bookingId);
            BookingEvent.ParticipantBooked instructorBooked = new BookingEvent.ParticipantBooked(entityId, cmd.instructorId,
                    Participant.ParticipantType.INSTRUCTOR, cmd.bookingId);
            BookingEvent.ParticipantBooked aircraftBooked = new BookingEvent.ParticipantBooked(entityId, cmd.aircraftId,
                    Participant.ParticipantType.AIRCRAFT, cmd.bookingId);
            List<BookingEvent> events = List.of(studentBooked, instructorBooked, aircraftBooked);
            return effects().persistAll(events).thenReply(newState -> Done.done());

        }

    }

    // NOTE: canceling a booking should produce 3
    // `ParticipantCanceled` events
    public Effect<Done> cancelBooking(String bookingId) {
        List<Timeslot.Booking> bookingsToCancel = currentState().findBooking(bookingId);
        if (bookingsToCancel.isEmpty()) {
            return effects().error("Cannot cancel booking " + bookingId + " as booking does not exist.");
        } else {
            List<BookingEvent> events = bookingsToCancel.stream().map(booking -> (BookingEvent) new BookingEvent.ParticipantCanceled(entityId, booking.participant().id(),
                    booking.participant().participantType(), bookingId)).toList();
            return effects().persistAll(events).thenReply(newState -> Done.done());
        }
    }

    public ReadOnlyEffect<Timeslot> getSlot() {
        return effects().reply(currentState());
    }

    @Override
    public Timeslot emptyState() {
        return new Timeslot(
                // NOTE: these are just estimates for capacity based on it being a sample
                HashSet.newHashSet(10), HashSet.newHashSet(10));
    }

    @Override
    public Timeslot applyEvent(BookingEvent event) {
        return switch (event) {
            case BookingEvent.ParticipantCanceled cancelled -> currentState().cancelBooking(cancelled.bookingId());
            case BookingEvent.ParticipantBooked booked ->
                currentState().book(booked);
            case BookingEvent.ParticipantMarkedAvailable available ->
                currentState().reserve(available);
            case BookingEvent.ParticipantUnmarkedAvailable unavailable ->
                currentState().unreserve(unavailable);
        };
    }

    public sealed interface Command {
        record MarkSlotAvailable(Participant participant) implements Command {
        }

        record UnmarkSlotAvailable(Participant participant) implements Command {
        }

        record BookReservation(
                String studentId, String aircraftId, String instructorId, String bookingId)
                implements Command {
        }
    }
}
