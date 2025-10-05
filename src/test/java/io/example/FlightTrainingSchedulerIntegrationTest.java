package io.example;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import io.example.api.FlightEndpoint;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

public class FlightTrainingSchedulerIntegrationTest extends TestKitSupport {

    @BeforeEach
    public void cleanup(){
        httpClient
                .DELETE("/flight/availability/bestslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("alice", "student"))
                .invoke();
        httpClient
                .DELETE("/flight/availability/bestslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("superplane", "aircraft"))
                .invoke();
        httpClient
                .DELETE("/flight/availability/bestslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("superteacher", "instructor"))
                .invoke();
        httpClient
                .DELETE("/flight/availability/worstslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("alice", "student"))
                .invoke();
        httpClient
                .DELETE("/flight/availability/worstslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("superplane", "aircraft"))
                .invoke();
        httpClient
                .DELETE("/flight/availability/worstslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("superteacher", "instructor"))
                .invoke();

        httpClient
                .DELETE("/flight/bookings/bestslot/booking1")
                .invoke();
    }

    @Test
    public void slotEmptyWhenNothingMadeAvailable() {
        System.out.println("starting test 1");
        var response = httpClient
                .GET("/flight/availability/public/bestslot")
                .invoke();

        Assertions.assertThat(response.httpResponse().status()).isEqualTo(StatusCodes.OK);
        Assertions.assertThat(response.body().utf8String()).isEqualTo("{\"bookings\":[],\"available\":[]}");

    }

    @Test
    public void participantGoneWhenMarkedAvailableThenUnavailable(){
        System.out.println("starting test 2");
        httpClient
                .POST("/flight/availability/bestslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("alice", "student"))
                .invoke();

        httpClient
                .DELETE("/flight/availability/bestslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("alice", "student"))
                .invoke();

        var slotResponse = httpClient
                .GET("/flight/availability/public/bestslot")
                .invoke();

        Assertions.assertThat(slotResponse.httpResponse().status()).isEqualTo(StatusCodes.OK);
        Assertions.assertThat(slotResponse.body().utf8String()).isEqualTo("{\"bookings\":[],\"available\":[]}");

        var aliceResponse = httpClient
                .GET("/flight/slots/alice/available")
                .invoke();
        Assertions.assertThat(aliceResponse.httpResponse().status()).isEqualTo(StatusCodes.OK);
        Assertions.assertThat(aliceResponse.body().utf8String()).isEqualTo("{\"slots\":[]}");
    }

    @Test
    public void participantsShowAsAvailableAfterMarkedAvailable() {
        System.out.println("starting test 3");
        httpClient
                .POST("/flight/availability/bestslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("alice", "student"))
                .invoke();
        httpClient
                .POST("/flight/availability/bestslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("superplane", "aircraft"))
                .invoke();

        httpClient
                .POST("/flight/availability/worstslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("alice", "student"))
                .invoke();
        httpClient
                .POST("/flight/availability/worstslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("superteacher", "instructor"))
                .invoke();

        var bestslotAvailabilityResponse = httpClient
                .GET("/flight/availability/public/bestslot")
                .invoke();
        Assertions.assertThat(bestslotAvailabilityResponse.httpResponse().status()).isEqualTo(StatusCodes.OK);
        Assertions.assertThat(bestslotAvailabilityResponse.body().utf8String())
                .isEqualTo("{\"bookings\":[],\"available\":[{\"id\":\"alice\",\"participantType\":\"STUDENT\"},{\"id\":\"superplane\",\"participantType\":\"AIRCRAFT\"}]}");

        var worstSlotAvailabilityResponse = httpClient
                .GET("/flight/availability/public/worstslot")
                .invoke();
        Assertions.assertThat(worstSlotAvailabilityResponse.httpResponse().status()).isEqualTo(StatusCodes.OK);
        Assertions.assertThat(worstSlotAvailabilityResponse.body().utf8String())
                .isEqualTo("{\"bookings\":[],\"available\":[{\"id\":\"alice\",\"participantType\":\"STUDENT\"},{\"id\":\"superteacher\",\"participantType\":\"INSTRUCTOR\"}]}");

        var aliceAvailabilitySlots = httpClient
                .GET("/flight/slots/alice/available")
                .invoke();

        var superplaneAvailabilitySlots = httpClient
                .GET("/flight/slots/superplane/available")
                .invoke();

        var superteacherAvailabilitySlots = httpClient
                .GET("/flight/slots/superteacher/available")
                .invoke();

        Assertions.assertThat(aliceAvailabilitySlots.httpResponse().status()).isEqualTo(StatusCodes.OK);
        Assertions.assertThat(aliceAvailabilitySlots.body().utf8String()).isEqualTo("{\"slots\":[]}");

        Assertions.assertThat(superplaneAvailabilitySlots.httpResponse().status()).isEqualTo(StatusCodes.OK);
        Assertions.assertThat(superplaneAvailabilitySlots.body().utf8String()).isEqualTo("{\"slots\":[]}");

        Assertions.assertThat(superteacherAvailabilitySlots.httpResponse().status()).isEqualTo(StatusCodes.OK);
        Assertions.assertThat(superteacherAvailabilitySlots.body().utf8String()).isEqualTo("{\"slots\":[]}");

        httpClient
                .DELETE("/flight/availability/bestslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("alice", "student"))
                .invoke();
        httpClient
                .DELETE("/flight/availability/bestslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("superplane", "aircraft"))
                .invoke();

        httpClient
                .DELETE("/flight/availability/worstslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("alice", "student"))
                .invoke();
        httpClient
                .DELETE("/flight/availability/worstslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("superteacher", "instructor"))
                .invoke();
    }

    @Test
    public void bookingTest() throws InterruptedException {
        System.out.println("starting test 4");
        httpClient
                .POST("/flight/availability/bestslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("alice", "student"))
                .invoke();
        httpClient
                .POST("/flight/availability/bestslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("superplane", "aircraft"))
                .invoke();

        var incompleteBookingResponse = httpClient
                .POST("/flight/bookings/bestSlot")
                .withRequestBody(new FlightEndpoint.BookingRequest("alice", "superplane", "superteacher", "booking1"))
                .invoke();

        Assertions.assertThat(incompleteBookingResponse.status()).isEqualTo(StatusCodes.BAD_REQUEST);
        Assertions.assertThat(incompleteBookingResponse.body().utf8String()).isEqualTo("Cannot book slot: one or more participants is unavailable.");

        httpClient
                .POST("/flight/availability/bestslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("superteacher", "instructor"))
                .invoke();

        var completeBookingResponse = httpClient
                .POST("/flight/bookings/bestslot")
                .withRequestBody(new FlightEndpoint.BookingRequest("alice", "superplane", "superteacher", "booking1"))
                .invoke();

        Assertions.assertThat(completeBookingResponse.status()).isEqualTo(StatusCodes.CREATED);
        Assertions.assertThat(completeBookingResponse.body().utf8String()).isEqualTo("");

        var doubleBookingResponse = httpClient
                .POST("/flight/bookings/bestSlot")
                .withRequestBody(new FlightEndpoint.BookingRequest("alice", "superplane", "superteacher", "booking1"))
                .invoke();

        Assertions.assertThat(doubleBookingResponse.status()).isEqualTo(StatusCodes.BAD_REQUEST);
        Assertions.assertThat(doubleBookingResponse.body().utf8String()).isEqualTo("Cannot book slot: one or more participants is unavailable.");

        var availabilityResponse = httpClient
                .POST("/flight/availability/bestslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("alice", "student"))
                .invoke();

        Assertions.assertThat(availabilityResponse.status()).isEqualTo(StatusCodes.BAD_REQUEST);
        Assertions.assertThat(availabilityResponse.body().utf8String()).isEqualTo("Participant alice already booked for this slot. To mark the participant available, please cancel the booking first.");

        var unavailabilityResponse = httpClient
                .DELETE("/flight/availability/bestslot")
                .withRequestBody(new FlightEndpoint.AvailabilityRequest("alice", "student"))
                .invoke();

        Assertions.assertThat(unavailabilityResponse.status()).isEqualTo(StatusCodes.BAD_REQUEST);
        Assertions.assertThat(unavailabilityResponse.body().utf8String()).isEqualTo("Participant alice currently booked for this slot. To mark the participant unavailable, cancel the booking.");

        var bestslotAvailabilityResponse = httpClient
                .GET("/flight/availability/public/bestslot")
                .invoke();
        Assertions.assertThat(bestslotAvailabilityResponse.httpResponse().status()).isEqualTo(StatusCodes.OK);
        Assertions.assertThat(bestslotAvailabilityResponse.body().utf8String())
                .isEqualTo("{\"bookings\":[{\"participant\":{\"id\":\"alice\",\"participantType\":\"STUDENT\"},\"bookingId\":\"booking1\"},{\"participant\":{\"id\":\"superplane\",\"participantType\":\"AIRCRAFT\"},\"bookingId\":\"booking1\"},{\"participant\":{\"id\":\"superteacher\",\"participantType\":\"INSTRUCTOR\"},\"bookingId\":\"booking1\"}],\"available\":[]}");

        var aliceBookedSlots = httpClient
                .GET("/flight/slots/alice/booked")
                .invoke();

        Assertions.assertThat(aliceBookedSlots.httpResponse().status()).isEqualTo(StatusCodes.OK);
        Assertions.assertThat(aliceBookedSlots.body().utf8String()).isEqualTo("{\"slots\":[]}");

        httpClient
                .DELETE("/flight/bookings/bestslot/booking1")
                .invoke();

        var postCancelledBestslotAvailabilityResponse = httpClient
                .GET("/flight/availability/public/bestslot")
                .invoke();
        Assertions.assertThat(postCancelledBestslotAvailabilityResponse.httpResponse().status()).isEqualTo(StatusCodes.OK);
        Assertions.assertThat(postCancelledBestslotAvailabilityResponse.body().utf8String())
                .isEqualTo("{\"bookings\":[],\"available\":[]}");

        var postCancelledAliceBookedSlots = httpClient
                .GET("/flight/slots/alice/booked")
                .invoke();

        Assertions.assertThat(postCancelledAliceBookedSlots.httpResponse().status()).isEqualTo(StatusCodes.OK);
        Assertions.assertThat(postCancelledAliceBookedSlots.body().utf8String()).isEqualTo("{\"slots\":[]}");


    }

}
