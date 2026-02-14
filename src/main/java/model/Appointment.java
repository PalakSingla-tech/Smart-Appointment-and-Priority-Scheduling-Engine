package model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "appointment")
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long appointmentId;

    @ManyToOne
    private Doctor doctor;

    @ManyToOne
    private Patient patient;

    private String patientName;
    private LocalDate date;
    private String timeSlot;

    @Enumerated(EnumType.STRING)
    private Priority priority;
    private LocalDateTime requestTime; // to break ties when priority is same

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    public enum BookingStatus {
        BOOKED,
        CANCELLED
    }

    @Getter
    public enum Priority {
        EMERGENCY(1),
        VIP(2),
        REGULAR(3);

        private final int level;

        Priority(int level) {
            this.level = level;
        }
    }
}
