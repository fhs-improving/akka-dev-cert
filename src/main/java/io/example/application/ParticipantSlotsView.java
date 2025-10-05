package io.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import io.example.application.ParticipantSlotEntity.Event;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("view-participant-slots")
public class ParticipantSlotsView extends View {

    private static Logger logger = LoggerFactory.getLogger(ParticipantSlotsView.class);


    public interface SlotStatus {
        String AVAILABLE = "available";
        String BOOKED = "booked";
    }

    @Consume.FromEventSourcedEntity(ParticipantSlotEntity.class)
    public static class ParticipantSlotsViewUpdater extends TableUpdater<SlotRow> {

        public Effect<SlotRow> onEvent(ParticipantSlotEntity.Event event) {
            return switch(event) {
                case Event.UnmarkedAvailable unused -> effects().deleteRow();
                case Event.MarkedAvailable available -> {
                    SlotRow row = new SlotRow(available.slotId(), available.participantId(),
                            available.participantType().toString(), "", SlotStatus.AVAILABLE);
                    yield effects().updateRow(row);
                }
                case Event.Booked booked -> {
                    SlotRow row = new SlotRow(booked.slotId(), booked.participantId(),
                            booked.participantType().toString(), booked.bookingId(), SlotStatus.BOOKED);
                    yield effects().updateRow(row);
                }
                case Event.Canceled unused -> {
                    logger.info("In the view, canceling: " + unused);
                    yield effects().deleteRow();
                }
            };
        }
    }

    public record SlotRow(
            String slotId,
            String participantId,
            String participantType,
            String bookingId,
            String status) {
    }

    public record ParticipantStatusInput(String participantId, String status) {
    }

    public record SlotList(List<SlotRow> slots) {
    }

    @Query("SELECT * AS slots FROM slots_by_participants WHERE participantId = :participantId")
    public QueryEffect<SlotList> getSlotsByParticipant(String participantId) {
        return queryResult();
    }

    @Query("SELECT * AS slots FROM slots_by_participants WHERE participantId = :participantId AND status = :status")
    public QueryEffect<SlotList> getSlotsByParticipantAndStatus(ParticipantStatusInput input) {
        return queryResult();
    }
}
