package service;

import model.Appointment;
import model.Doctor;
import model.Patient;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AppointmentService {
    // Invalid Time Slot Exception
    public static class InvalidTimeSlotException extends RuntimeException {
        public InvalidTimeSlotException(String message) {
            super(message);
        }
    }

    // Already Booked Slot Exception
    public static class SlotAlreadyBookedException extends RuntimeException {
        public SlotAlreadyBookedException(String message) {
            super(message);
        }
    }

    // Lower Priority Appointment
    public static class LowerPriorityAppointmentException extends RuntimeException {
        public LowerPriorityAppointmentException(String message) {
            super(message);
        }
    }

    // Appointment Limit Exceeded Exception
    public static class AppointmentLimitExceededException extends RuntimeException {
        public AppointmentLimitExceededException(String message) {
            super(message);
        }
    }

    // Invalid Appointment (General Exception)
    public static class InvalidAppointmentException extends RuntimeException {
        public InvalidAppointmentException(String message) {
            super(message);
        }
    }

    // Invalid Priority Exception
    public static class InvalidPriorityException extends RuntimeException {
        public InvalidPriorityException(String message) {
            super(message);
        }
    }

    private final SessionFactory sessionFactory;

    public AppointmentService(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public synchronized void bookAppointment(Doctor doctor, String patientName, LocalDate appointmentDate,
            String timeSlot,
            Appointment.Priority priority) {
        // Validation logic...
        if (doctor == null)
            throw new InvalidAppointmentException("Doctor information is missing!");
        if (patientName == null || patientName.trim().isEmpty())
            throw new InvalidAppointmentException("Patient Name cannot be empty");
        if (!isValidTimeSlotFormat(timeSlot))
            throw new InvalidTimeSlotException("Invalid format. Use hh:mm-hh:mm");
        if (appointmentDate.isBefore(LocalDate.now()))
            throw new InvalidAppointmentException("Cannot book an appointment for a past date!");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("H:mm");
        LocalTime doctorStartTime = LocalTime.parse(doctor.getStartTime(), formatter);
        LocalTime doctorEndTime = LocalTime.parse(doctor.getEndTime(), formatter);

        String[] times = timeSlot.split("-");
        LocalTime chosenStart = LocalTime.parse(times[0], formatter);
        LocalTime chosenEnd = LocalTime.parse(times[1], formatter);

        if (chosenStart.isBefore(doctorStartTime) || chosenEnd.isAfter(doctorEndTime)) {
            throw new InvalidTimeSlotException("Time Slot outside doctor's working hours!");
        }

        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();

            // Find or Create Patient
            Query<Patient> patientQuery = session.createQuery("from Patient where name = :name", Patient.class);
            patientQuery.setParameter("name", patientName);
            Patient patient = patientQuery.uniqueResult();

            if (patient == null) {
                patient = new Patient();
                patient.setName(patientName);
                session.persist(patient);
            }

            // Check daily limit
            Query<Long> countQuery = session.createQuery(
                    "select count(a) from Appointment a where a.date = :date and a.status = :status", Long.class);
            countQuery.setParameter("date", appointmentDate);
            countQuery.setParameter("status", Appointment.BookingStatus.BOOKED);
            long dailyCount = countQuery.uniqueResult();

            // Slot conflict logic
            Query<Appointment> conflictQuery = session.createQuery(
                    "from Appointment a where a.date = :date and a.timeSlot = :slot and a.status = :status",
                    Appointment.class);
            conflictQuery.setParameter("date", appointmentDate);
            conflictQuery.setParameter("slot", timeSlot);
            conflictQuery.setParameter("status", Appointment.BookingStatus.BOOKED);
            Appointment conflictingAppt = conflictQuery.uniqueResult();

            if (conflictingAppt != null) {
                if (priority.getLevel() < conflictingAppt.getPriority().getLevel()) {
                    conflictingAppt.setStatus(Appointment.BookingStatus.CANCELLED);
                    session.merge(conflictingAppt);
                    System.out.println("Higher priority appointment replaced existing one (ID: "
                            + conflictingAppt.getAppointmentId() + ")");
                } else {
                    transaction.rollback();
                    throw new SlotAlreadyBookedException("Slot already booked!");
                }
            } else if (dailyCount >= 5) {
                transaction.rollback();
                throw new AppointmentLimitExceededException("Appointments Limit Exceeded (Max 5 per day)");
            }

            // Create NEW appointment
            Appointment appointment = new Appointment();
            appointment.setPatientName(patientName);
            appointment.setPatient(patient); // Link the Patient entity
            appointment.setDoctor(doctor);
            appointment.setDate(appointmentDate);
            appointment.setTimeSlot(timeSlot);
            appointment.setPriority(priority);
            appointment.setRequestTime(LocalDateTime.now());
            appointment.setStatus(Appointment.BookingStatus.BOOKED);

            session.persist(appointment);
            transaction.commit();

            System.out.println("Appointment booked successfully!");
            System.out.println("Your Appointment ID is: " + appointment.getAppointmentId() + "\n");
        }
    }

    public synchronized void cancelAppointment(int id) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            Appointment appt = session.find(Appointment.class, (long) id);
            if (appt != null && appt.getStatus() == Appointment.BookingStatus.BOOKED) {
                appt.setStatus(Appointment.BookingStatus.CANCELLED);
                session.merge(appt);
                transaction.commit();
                System.out.println("Appointment cancelled successfully!");
            } else {
                System.out.println("No active Appointment found with this id!");
                transaction.rollback();
            }
        }
    }

    public synchronized void viewAppointment() {
        try (Session session = sessionFactory.openSession()) {
            Query<Appointment> query = session.createQuery(
                    "from Appointment a where a.status = :status order by a.priority asc, a.requestTime asc",
                    Appointment.class);
            query.setParameter("status", Appointment.BookingStatus.BOOKED);
            List<Appointment> appointments = query.list();

            if (appointments.isEmpty()) {
                System.out.println("No Appointments!");
                return;
            }

            System.out.println("-------------All Appointments-----------------");
            System.out.println("ID \t Patient Name \t Doctor \t Slot \t Date \t Priority");
            for (Appointment appt : appointments) {
                System.out.println(appt.getAppointmentId() + " \t " + appt.getPatientName() + " \t " +
                        appt.getDoctor().getName() + " \t " + appt.getTimeSlot() + " \t " + appt.getDate() + " \t "
                        + appt.getPriority());
            }
        }
    }

    public synchronized void rescheduleAppointment(int id, LocalDate date, String timeSlot) {
        if (!isValidTimeSlotFormat(timeSlot))
            throw new InvalidTimeSlotException("Invalid format. Use hh:mm-hh:mm");
        if (date.isBefore(LocalDate.now()))
            throw new InvalidAppointmentException("Cannot reschedule to a past date!");

        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            Appointment appt = session.find(Appointment.class, (long) id);

            if (appt == null) {
                System.out.println("No appointment found with ID: " + id);
                transaction.rollback();
                return;
            }

            Doctor doctor = appt.getDoctor();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("H:mm");
            LocalTime doctorStartTime = LocalTime.parse(doctor.getStartTime(), formatter);
            LocalTime doctorEndTime = LocalTime.parse(doctor.getEndTime(), formatter);

            String[] times = timeSlot.split("-");
            LocalTime chosenStart = LocalTime.parse(times[0], formatter);
            LocalTime chosenEnd = LocalTime.parse(times[1], formatter);

            if (chosenStart.isBefore(doctorStartTime) || chosenEnd.isAfter(doctorEndTime)) {
                transaction.rollback();
                throw new InvalidTimeSlotException("Time Slot outside doctor's working hours!");
            }

            // Check if slot is already booked by another active appointment
            Query<Appointment> conflictQuery = session.createQuery(
                    "from Appointment a where a.date = :date and a.timeSlot = :slot and a.status = :status and a.appointmentId != :id",
                    Appointment.class);
            conflictQuery.setParameter("date", date);
            conflictQuery.setParameter("slot", timeSlot);
            conflictQuery.setParameter("status", Appointment.BookingStatus.BOOKED);
            conflictQuery.setParameter("id", (long) id);
            Appointment conflictingAppt = conflictQuery.uniqueResult();

            if (conflictingAppt != null) {
                if (appt.getPriority().getLevel() < conflictingAppt.getPriority().getLevel()) {
                    conflictingAppt.setStatus(Appointment.BookingStatus.CANCELLED);
                    session.merge(conflictingAppt);
                    System.out.println("Higher priority appointment replaced existing one (ID: "
                            + conflictingAppt.getAppointmentId() + ")");
                } else {
                    transaction.rollback();
                    throw new SlotAlreadyBookedException(
                            "The requested slot is already taken by a higher or equal priority appointment!");
                }
            }

            // Update appointment
            appt.setDate(date);
            appt.setTimeSlot(timeSlot);
            appt.setStatus(Appointment.BookingStatus.BOOKED);
            appt.setRequestTime(LocalDateTime.now()); // Update request time for priority tie-breaking

            session.merge(appt); // Explicitly merge
            transaction.commit();
            System.out.println("Appointment rescheduled successfully!");
        }
    }

    public List<Doctor> getAllDoctors() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("from Doctor", Doctor.class).list();
        }
    }

    public Doctor getDoctorById(int id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Doctor.class, id);
        }
    }

    private boolean isValidTimeSlotFormat(String timeSlot) {
        return timeSlot.matches("\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}");
    }
}
