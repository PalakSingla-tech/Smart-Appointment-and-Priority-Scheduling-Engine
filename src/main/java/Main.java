import model.Appointment;
import model.Doctor;
import service.AppointmentService;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.lang.System.exit;
import org.hibernate.SessionFactory;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

public class Main {
    public static void main(String[] args) {
        SessionFactory sf = new Configuration().configure().buildSessionFactory();
        Scanner scn = new Scanner(System.in);
        AppointmentService service = new AppointmentService(sf);

        // Seed doctors if none exist
        try (Session session = sf.openSession()) {
            Long count = session.createQuery("select count(d) from Doctor d", Long.class).uniqueResult();
            if (count == 0) {
                Transaction tx = session.beginTransaction();
                // Using standard constructor or setters since Lombok might not generate
                // specific ones
                Doctor d1 = new Doctor();
                d1.setName("Dr. Smith");
                d1.setSpecialization("Cardiologist");
                d1.setStartTime("09:00");
                d1.setEndTime("17:00");
                session.persist(d1);

                Doctor d2 = new Doctor();
                d2.setName("Dr. Jones");
                d2.setSpecialization("Dermatologist");
                d2.setStartTime("10:00");
                d2.setEndTime("18:00");
                session.persist(d2);

                Doctor d3 = new Doctor();
                d3.setName("Dr. Taylor");
                d3.setSpecialization("General Physician");
                d3.setStartTime("08:00");
                d3.setEndTime("16:00");
                session.persist(d3);

                tx.commit();
                System.out.println("Seeded initial doctors into database.");
            }
        }

        while (true) {
            System.out.println("Welcome to Smart Appointment");
            System.out.println("Menu: ");
            System.out.println("1. Book Appointment");
            System.out.println("2. Cancel Appointment");
            System.out.println("3. View Appointment");
            System.out.println("4. Reschedule Appointment");
            System.out.println("5. Simulate Concurrent Bookings");
            System.out.println("6. Exit");
            System.out.println("-----------------------------------");

            System.out.print("Enter your choice : ");
            int option = 0;
            if (scn.hasNextInt()) {
                option = scn.nextInt();
                scn.nextLine(); // consume newline
            } else {
                scn.nextLine(); // consume invalid input
                System.out.println("Invalid input!");
                continue;
            }

            try {
                if (option == 1) {
                    // Book Appointment
                    System.out.println("\nAvailable Doctors:");
                    List<Doctor> doctors = service.getAllDoctors();
                    for (Doctor d : doctors) {
                        System.out.println(d.getId() + ". " + d.getName() + " (" + d.getSpecialization() + ") ["
                                + d.getStartTime() + "-" + d.getEndTime() + "]");
                    }

                    System.out.print("Select Doctor ID: ");
                    int docId = 0;
                    if (scn.hasNextInt()) {
                        docId = scn.nextInt();
                        scn.nextLine();
                    } else {
                        scn.nextLine();
                        System.out.println("Invalid ID!");
                        continue;
                    }

                    Doctor selectedDoc = service.getDoctorById(docId);
                    if (selectedDoc == null) {
                        System.out.println("Doctor not found!");
                        continue;
                    }

                    System.out.println(
                            "Booking for: " + selectedDoc.getName() + " (" + selectedDoc.getSpecialization() + ")");

                    System.out.print("Enter Patient Name: ");
                    String name = scn.nextLine();

                    System.out.print("Choose time slot (e.g., 10:00-10:30): ");
                    String timeSlot = scn.nextLine();

                    System.out.print("Enter appointment date (yyyy-MM-dd): ");
                    String dateStr = scn.nextLine();

                    System.out.print("Enter Priority (Emergency | VIP | Regular): ");
                    String priorityStr = scn.nextLine();

                    try {
                        LocalDate date = LocalDate.parse(dateStr);
                        Appointment.Priority priority = Appointment.Priority.valueOf(priorityStr.toUpperCase());

                        service.bookAppointment(selectedDoc, name, date, timeSlot, priority);
                    } catch (DateTimeParseException e) {
                        System.out.println("Error: Invalid date format. Please use yyyy-MM-dd.");
                    } catch (IllegalArgumentException e) {
                        System.out.println("Error: Invalid priority format.");
                    }

                } else if (option == 2) {
                    System.out.print("Enter appointment ID to cancel : ");
                    if (scn.hasNextInt()) {
                        int id = scn.nextInt();
                        scn.nextLine();
                        service.cancelAppointment(id);
                    } else {
                        System.out.println("Invalid ID format!");
                        scn.nextLine();
                    }
                } else if (option == 3) {
                    service.viewAppointment();
                } else if (option == 4) {
                    System.out.print("Enter appointment ID to reschedule : ");
                    if (scn.hasNextInt()) {
                        int id = scn.nextInt();
                        scn.nextLine();
                        System.out.print("Enter new date (yyyy-MM-dd): ");
                        String dateInput = scn.nextLine();
                        System.out.print("Enter new time slot (e.g., 10:00-10:30): ");
                        String timeSlot = scn.nextLine();
                        try {
                            LocalDate date = LocalDate.parse(dateInput);
                            service.rescheduleAppointment(id, date, timeSlot);
                        } catch (DateTimeParseException e) {
                            System.out.println("Error: Invalid date format. Please use yyyy-MM-dd.");
                        }
                    } else {
                        System.out.println("Invalid ID format!");
                        scn.nextLine();
                    }
                } else if (option == 5) {
                    // Simulate Concurrent Bookings
                    System.out.println("Simulating concurrent bookings...");
                    simulateConcurrentBookings(service, sf);
                } else if (option == 6) {
                    exit(0);
                } else {
                    System.out.println("Invalid option!");
                }
            } catch (RuntimeException e) {
                System.out.println("Error : " + e.getMessage());
            }
        }
    }

    private static void simulateConcurrentBookings(AppointmentService service, SessionFactory sf) {
        List<Doctor> doctors = service.getAllDoctors();
        if (doctors.isEmpty()) {
            System.out.println("No doctors available for simulation!");
            return;
        }
        Doctor doctor = doctors.get(0); // Use the first seeded doctor

        ExecutorService executor = Executors.newFixedThreadPool(5);
        LocalDate date = LocalDate.now().plusDays(1);
        String timeSlot = "10:00-10:30";

        // Task 1: Regular Booking
        executor.submit(() -> {
            try {
                System.out.println("Thread-1 trying to book Regular...");
                service.bookAppointment(doctor, "User1-Regular", date, timeSlot, Appointment.Priority.REGULAR);
            } catch (Exception e) {
                System.out.println("Thread-1 Error: " + e.getMessage());
            }
        });

        // Task 2: Another Regular Booking (Collision)
        executor.submit(() -> {
            try {
                Thread.sleep(100); // slight delay
                System.out.println("Thread-2 trying to book Regular (Collision)...");
                service.bookAppointment(doctor, "User2-Regular", date, timeSlot, Appointment.Priority.REGULAR);
            } catch (Exception e) {
                System.out.println("Thread-2 Error: " + e.getMessage());
            }
        });

        // Task 3: VIP Booking (Should replace Regular)
        executor.submit(() -> {
            try {
                Thread.sleep(300);
                System.out.println("Thread-3 trying to book VIP...");
                service.bookAppointment(doctor, "User3-VIP", date, timeSlot, Appointment.Priority.VIP);
            } catch (Exception e) {
                System.out.println("Thread-3 Error: " + e.getMessage());
            }
        });

        // Task 4: Emergency Booking (Should replace VIP)
        executor.submit(() -> {
            try {
                Thread.sleep(500);
                System.out.println("Thread-4 trying to book Emergency...");
                service.bookAppointment(doctor, "User4-Emergency", date, timeSlot, Appointment.Priority.EMERGENCY);
            } catch (Exception e) {
                System.out.println("Thread-4 Error: " + e.getMessage());
            }
        });

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        service.viewAppointment();
    }
}