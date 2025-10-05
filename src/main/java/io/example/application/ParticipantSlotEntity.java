package io.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import io.example.domain.Participant.ParticipantType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("participant-slot")
public class ParticipantSlotEntity
        extends EventSourcedEntity<ParticipantSlotEntity.State, ParticipantSlotEntity.Event> {

    private static Logger logger = LoggerFactory.getLogger(ParticipantSlotEntity.class);

    public Effect<Done> unmarkAvailable(ParticipantSlotEntity.Commands.UnmarkAvailable unmark) {
        Event.UnmarkedAvailable event = new Event.UnmarkedAvailable(unmark.slotId, unmark.participantId, unmark.participantType);
        return effects()
                .persist(event)
                .thenReply(newState -> Done.done());
    }

    public Effect<Done> markAvailable(ParticipantSlotEntity.Commands.MarkAvailable mark) {
        Event.MarkedAvailable event = new Event.MarkedAvailable(mark.slotId, mark.participantId, mark.participantType);
        return effects()
                .persist(event)
                .thenReply(newState -> Done.done());
    }

    public Effect<Done> book(ParticipantSlotEntity.Commands.Book book) {
        Event.Booked event = new Event.Booked(book.slotId, book.participantId, book.participantType, book.bookingId);
        return effects()
                .persist(event)
                .thenReply(newState -> Done.done());
    }

    public Effect<Done> cancel(ParticipantSlotEntity.Commands.Cancel cancel) {
        Event.Canceled event = new Event.Canceled(cancel.slotId, cancel.participantId, cancel.participantType, cancel.bookingId);
        return effects()
                .persist(event)
                .thenReply(newState -> Done.done());
    }

    record State(
            String slotId, String participantId, ParticipantType participantType, String status) {
    }

    public sealed interface Commands {
        record MarkAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Commands {
        }

        record UnmarkAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Commands {
        }

        record Book(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Commands {
        }

        record Cancel(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Commands {
        }
    }

    public sealed interface Event {
        @TypeName("marked-available")
        record MarkedAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Event {
        }

        @TypeName("unmarked-available")
        record UnmarkedAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Event {
        }

        @TypeName("participant-booked")
        record Booked(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Event {
        }

        @TypeName("participant-canceled")
        record Canceled(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Event {
        }
    }

    @Override
    public ParticipantSlotEntity.State applyEvent(ParticipantSlotEntity.Event event) {
        return switch (event) {
            case Event.MarkedAvailable markedAvailable -> new ParticipantSlotEntity.State(
                    markedAvailable.slotId, markedAvailable.participantId, markedAvailable.participantType, ParticipantSlotsView.SlotStatus.AVAILABLE);
            case Event.UnmarkedAvailable unmarkedAvailable -> new ParticipantSlotEntity.State(
                    unmarkedAvailable.slotId, unmarkedAvailable.participantId, unmarkedAvailable.participantType, null);
            case Event.Canceled cancelled -> new ParticipantSlotEntity.State(
                    cancelled.slotId, cancelled.participantId, cancelled.participantType, null);
            case Event.Booked booked -> new ParticipantSlotEntity.State(
                    booked.slotId, booked.participantId, booked.participantType, ParticipantSlotsView.SlotStatus.BOOKED);
        };
    }
}
