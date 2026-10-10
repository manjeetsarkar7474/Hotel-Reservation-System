import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;


public class HotelReservationSystem {

    // =========================== MODEL LAYER ===========================

    enum RoomCategory {
        STANDARD(2000.0, "Double bed, Wi-Fi, TV"),
        DELUXE(3500.0, "King bed, Wi-Fi, TV, Mini-bar, City view"),
        SUITE(6000.0, "King bed, Living area, Jacuzzi, Mini-bar, Breakfast");

        private final double pricePerNight;
        private final String amenities;

        RoomCategory(double pricePerNight, String amenities) {
            this.pricePerNight = pricePerNight;
            this.amenities = amenities;
        }

        public double getPricePerNight() { return pricePerNight; }
        public String getAmenities() { return amenities; }
    }

    enum BookingStatus { CONFIRMED, CANCELLED }

    enum PaymentMethod { CARD, UPI, CASH }

    static class Room {
        private final int roomNumber;
        private final RoomCategory category;

        Room(int roomNumber, RoomCategory category) {
            this.roomNumber = roomNumber;
            this.category = category;
        }

        int getRoomNumber() { return roomNumber; }
        RoomCategory getCategory() { return category; }
        double getPrice() { return category.getPricePerNight(); }

        String toFileString() { return roomNumber + "|" + category.name(); }

        static Room fromFileString(String line) {
            String[] p = line.split("\\|");
            return new Room(Integer.parseInt(p[0]), RoomCategory.valueOf(p[1]));
        }

        @Override
        public String toString() {
            return String.format("Room %-4d | %-9s | Rs.%-8.2f/night | %s",
                    roomNumber, category, getPrice(), category.getAmenities());
        }
    }

    static class Booking {
        private final String bookingId;
        private final int roomNumber;
        private final String guestName;
        private final String phone;
        private final LocalDate checkIn;
        private final LocalDate checkOut;
        private final double totalAmount;
        private final String transactionId;
        private final PaymentMethod paymentMethod;
        private BookingStatus status;
        private double refundAmount;

        Booking(String bookingId, int roomNumber, String guestName, String phone,
                LocalDate checkIn, LocalDate checkOut, double totalAmount,
                String transactionId, PaymentMethod paymentMethod,
                BookingStatus status, double refundAmount) {
            this.bookingId = bookingId;
            this.roomNumber = roomNumber;
            this.guestName = guestName;
            this.phone = phone;
            this.checkIn = checkIn;
            this.checkOut = checkOut;
            this.totalAmount = totalAmount;
            this.transactionId = transactionId;
            this.paymentMethod = paymentMethod;
            this.status = status;
            this.refundAmount = refundAmount;
        }

        String getBookingId() { return bookingId; }
        int getRoomNumber() { return roomNumber; }
        String getGuestName() { return guestName; }
        String getPhone() { return phone; }
        LocalDate getCheckIn() { return checkIn; }
        LocalDate getCheckOut() { return checkOut; }
        double getTotalAmount() { return totalAmount; }
        String getTransactionId() { return transactionId; }
        PaymentMethod getPaymentMethod() { return paymentMethod; }
        BookingStatus getStatus() { return status; }
        double getRefundAmount() { return refundAmount; }
        long getNights() { return ChronoUnit.DAYS.between(checkIn, checkOut); }

        void cancel(double refund) {
            this.status = BookingStatus.CANCELLED;
            this.refundAmount = refund;
        }

        /** True if this (confirmed) booking overlaps the requested [in, out) range. */
        boolean overlaps(LocalDate in, LocalDate out) {
            return status == BookingStatus.CONFIRMED
                    && in.isBefore(checkOut) && out.isAfter(checkIn);
        }

        String toFileString() {
            return String.join("|", bookingId, String.valueOf(roomNumber),
                    guestName.replace("|", " "), phone.replace("|", " "),
                    checkIn.toString(), checkOut.toString(),
                    String.valueOf(totalAmount), transactionId,
                    paymentMethod.name(), status.name(), String.valueOf(refundAmount));
        }

        static Booking fromFileString(String line) {
            String[] p = line.split("\\|", -1);
            return new Booking(p[0], Integer.parseInt(p[1]), p[2], p[3],
                    LocalDate.parse(p[4]), LocalDate.parse(p[5]),
                    Double.parseDouble(p[6]), p[7],
                    PaymentMethod.valueOf(p[8]), BookingStatus.valueOf(p[9]),
                    Double.parseDouble(p[10]));
        }

        void printDetails(Room room) {
            System.out.println("-------------------------------------------------");
            System.out.println(" BOOKING DETAILS");
            System.out.println("-------------------------------------------------");
            System.out.println(" Booking ID     : " + bookingId);
            System.out.println(" Status         : " + status);
            System.out.println(" Guest Name     : " + guestName);
            System.out.println(" Phone          : " + phone);
            System.out.println(" Room           : " + roomNumber
                    + (room != null ? " (" + room.getCategory() + ")" : ""));
            if (room != null) {
                System.out.println(" Amenities      : " + room.getCategory().getAmenities());
            }
            System.out.println(" Check-in       : " + checkIn);
            System.out.println(" Check-out      : " + checkOut);
            System.out.println(" Nights         : " + getNights());
            System.out.printf(" Total Paid     : Rs.%.2f%n", totalAmount);
            System.out.println(" Payment Method : " + paymentMethod);
            System.out.println(" Transaction ID : " + transactionId);
            if (status == BookingStatus.CANCELLED) {
                System.out.printf(" Refund Amount  : Rs.%.2f%n", refundAmount);
            }
            System.out.println("-------------------------------------------------");
        }
    }

    // ======================== PAYMENT SIMULATION =======================

    static class PaymentResult {
        final boolean success;
        final String transactionId;
        final String message;

        PaymentResult(boolean success, String transactionId, String message) {
            this.success = success;
            this.transactionId = transactionId;
            this.message = message;
        }
    }

    static class PaymentProcessor {
        private static final Random RANDOM = new Random();

        /** Simulates a payment. Validates inputs and adds a small random gateway failure chance. */
        static PaymentResult process(PaymentMethod method, double amount, String detail) {
            System.out.println("\nProcessing payment of Rs." + String.format("%.2f", amount)
                    + " via " + method + " ...");
            try { Thread.sleep(800); } catch (InterruptedException ignored) { }

            switch (method) {
                case CARD:
                    if (!detail.matches("\\d{16}")) {
                        return new PaymentResult(false, null, "Invalid card number (must be 16 digits).");
                    }
                    break;
                case UPI:
                    if (!detail.matches("[\\w.\\-]+@[\\w]+")) {
                        return new PaymentResult(false, null, "Invalid UPI ID (example: name@bank).");
                    }
                    break;
                case CASH:
                    break; // pay at the hotel desk
            }

            // 5% simulated gateway failure for online payments
            if (method != PaymentMethod.CASH && RANDOM.nextInt(100) < 5) {
                return new PaymentResult(false, null, "Payment gateway timeout. Please try again.");
            }

            String txn = "TXN" + System.currentTimeMillis() % 100000000L + RANDOM.nextInt(100);
            return new PaymentResult(true, txn, "Payment successful.");
        }
    }

    // ===================== PERSISTENCE (FILE I/O) ======================

    static class FileStorage {
        private final Path roomsFile;
        private final Path bookingsFile;

        FileStorage(String roomsPath, String bookingsPath) {
            this.roomsFile = Paths.get(roomsPath);
            this.bookingsFile = Paths.get(bookingsPath);
        }

        List<Room> loadRooms() {
            List<Room> rooms = new ArrayList<>();
            try {
                if (!Files.exists(roomsFile)) {
                    rooms = defaultRooms();
                    saveRooms(rooms);
                    return rooms;
                }
                for (String line : Files.readAllLines(roomsFile)) {
                    if (!line.trim().isEmpty()) rooms.add(Room.fromFileString(line.trim()));
                }
            } catch (IOException | RuntimeException e) {
                System.out.println("Error reading rooms file: " + e.getMessage());
            }
            return rooms;
        }

        void saveRooms(List<Room> rooms) {
            List<String> lines = rooms.stream().map(Room::toFileString).collect(Collectors.toList());
            try {
                Files.write(roomsFile, lines);
            } catch (IOException e) {
                System.out.println("Error saving rooms: " + e.getMessage());
            }
        }

        List<Booking> loadBookings() {
            List<Booking> list = new ArrayList<>();
            if (!Files.exists(bookingsFile)) return list;
            try {
                for (String line : Files.readAllLines(bookingsFile)) {
                    if (!line.trim().isEmpty()) list.add(Booking.fromFileString(line.trim()));
                }
            } catch (IOException | RuntimeException e) {
                System.out.println("Error reading bookings file: " + e.getMessage());
            }
            return list;
        }

        void saveBookings(List<Booking> bookings) {
            List<String> lines = bookings.stream().map(Booking::toFileString).collect(Collectors.toList());
            try {
                Files.write(bookingsFile, lines);
            } catch (IOException e) {
                System.out.println("Error saving bookings: " + e.getMessage());
            }
        }

        private List<Room> defaultRooms() {
            List<Room> rooms = new ArrayList<>();
            for (int i = 101; i <= 106; i++) rooms.add(new Room(i, RoomCategory.STANDARD));
            for (int i = 201; i <= 204; i++) rooms.add(new Room(i, RoomCategory.DELUXE));
            for (int i = 301; i <= 302; i++) rooms.add(new Room(i, RoomCategory.SUITE));
            return rooms;
        }
    }

    // ========================== SERVICE LAYER ==========================

    static class HotelService {
        private final FileStorage storage;
        private final List<Room> rooms;
        private final List<Booking> bookings;

        HotelService(FileStorage storage) {
            this.storage = storage;
            this.rooms = storage.loadRooms();
            this.bookings = storage.loadBookings();
        }

        Room findRoom(int roomNumber) {
            return rooms.stream().filter(r -> r.getRoomNumber() == roomNumber).findFirst().orElse(null);
        }

        boolean isAvailable(Room room, LocalDate in, LocalDate out) {
            return bookings.stream()
                    .noneMatch(b -> b.getRoomNumber() == room.getRoomNumber() && b.overlaps(in, out));
        }

        /** category may be null to search all categories. */
        List<Room> searchAvailable(LocalDate in, LocalDate out, RoomCategory category) {
            return rooms.stream()
                    .filter(r -> category == null || r.getCategory() == category)
                    .filter(r -> isAvailable(r, in, out))
                    .sorted(Comparator.comparingInt(Room::getRoomNumber))
                    .collect(Collectors.toList());
        }

        /** Creates a booking after a successful payment. Returns null if payment failed. */
        Booking makeReservation(Room room, String guest, String phone, LocalDate in, LocalDate out,
                                PaymentMethod method, String paymentDetail) {
            if (!isAvailable(room, in, out)) {
                System.out.println("Sorry, that room is no longer available for these dates.");
                return null;
            }
            long nights = ChronoUnit.DAYS.between(in, out);
            double total = nights * room.getPrice();

            PaymentResult result = PaymentProcessor.process(method, total, paymentDetail);
            System.out.println(result.message);
            if (!result.success) return null;

            String id = generateBookingId();
            Booking booking = new Booking(id, room.getRoomNumber(), guest, phone, in, out, total,
                    result.transactionId, method, BookingStatus.CONFIRMED, 0.0);
            bookings.add(booking);
            storage.saveBookings(bookings);
            return booking;
        }

        /**
         * Refund policy:
         *  - 2 or more days before check-in : 100% refund
         *  - Less than 2 days before check-in: 50% refund
         *  - On/after check-in day           : no refund
         */
        double calculateRefund(Booking b) {
            long daysToCheckIn = ChronoUnit.DAYS.between(LocalDate.now(), b.getCheckIn());
            if (daysToCheckIn >= 2) return b.getTotalAmount();
            if (daysToCheckIn >= 1) return b.getTotalAmount() * 0.5;
            return 0.0;
        }

        boolean cancelReservation(String bookingId) {
            Booking b = findBooking(bookingId);
            if (b == null) {
                System.out.println("No booking found with ID: " + bookingId);
                return false;
            }
            if (b.getStatus() == BookingStatus.CANCELLED) {
                System.out.println("This booking is already cancelled.");
                return false;
            }
            double refund = calculateRefund(b);
            b.cancel(refund);
            storage.saveBookings(bookings);
            System.out.printf("Booking %s cancelled. Refund of Rs.%.2f will be processed.%n",
                    b.getBookingId(), refund);
            return true;
        }

        Booking findBooking(String bookingId) {
            return bookings.stream()
                    .filter(b -> b.getBookingId().equalsIgnoreCase(bookingId))
                    .findFirst().orElse(null);
        }

        List<Booking> findByPhone(String phone) {
            return bookings.stream().filter(b -> b.getPhone().equals(phone)).collect(Collectors.toList());
        }

        List<Booking> getAllBookings() { return bookings; }
        List<Room> getAllRooms() { return rooms; }

        private String generateBookingId() {
            int max = 1000;
            for (Booking b : bookings) {
                try {
                    max = Math.max(max, Integer.parseInt(b.getBookingId().substring(2)));
                } catch (NumberFormatException ignored) { }
            }
            return "BK" + (max + 1);
        }
    }

    // ============================ UI LAYER =============================

    static class ConsoleApp {
        private final HotelService service;
        private final Scanner sc = new Scanner(System.in);

        ConsoleApp(HotelService service) { this.service = service; }

        void run() {
            System.out.println("=================================================");
            System.out.println("        HOTEL RESERVATION SYSTEM");
            System.out.println("=================================================");
            boolean running = true;
            while (running) {
                System.out.println("\n1. View all rooms & categories");
                System.out.println("2. Search available rooms");
                System.out.println("3. Book a room");
                System.out.println("4. View booking details");
                System.out.println("5. View my bookings (by phone)");
                System.out.println("6. Cancel a booking");
                System.out.println("7. View all bookings (admin)");
                System.out.println("0. Exit");
                int choice = readInt("Enter choice: ");
                switch (choice) {
                    case 1: showAllRooms(); break;
                    case 2: searchRooms(); break;
                    case 3: bookRoom(); break;
                    case 4: viewBooking(); break;
                    case 5: viewMyBookings(); break;
                    case 6: cancelBooking(); break;
                    case 7: viewAllBookings(); break;
                    case 0: running = false; break;
                    default: System.out.println("Invalid choice.");
                }
            }
            System.out.println("Thank you for using the Hotel Reservation System. Goodbye!");
        }

        private void showAllRooms() {
            System.out.println("\n--- All Rooms ---");
            for (RoomCategory c : RoomCategory.values()) {
                System.out.println("\n[" + c + "] Rs." + c.getPricePerNight() + "/night - " + c.getAmenities());
                service.getAllRooms().stream()
                        .filter(r -> r.getCategory() == c)
                        .forEach(r -> System.out.println("   Room " + r.getRoomNumber()));
            }
        }

        private void searchRooms() {
            LocalDate[] dates = readDateRange();
            if (dates == null) return;
            RoomCategory cat = readCategory(true);
            List<Room> available = service.searchAvailable(dates[0], dates[1], cat);
            if (available.isEmpty()) {
                System.out.println("No rooms available for the selected dates/category.");
                return;
            }
            long nights = ChronoUnit.DAYS.between(dates[0], dates[1]);
            System.out.println("\nAvailable rooms (" + nights + " night(s)):");
            for (Room r : available) {
                System.out.printf("%s | Total: Rs.%.2f%n", r, r.getPrice() * nights);
            }
        }

        private void bookRoom() {
            LocalDate[] dates = readDateRange();
            if (dates == null) return;
            RoomCategory cat = readCategory(true);
            List<Room> available = service.searchAvailable(dates[0], dates[1], cat);
            if (available.isEmpty()) {
                System.out.println("No rooms available for the selected dates/category.");
                return;
            }
            long nights = ChronoUnit.DAYS.between(dates[0], dates[1]);
            System.out.println("\nAvailable rooms:");
            for (Room r : available) {
                System.out.printf("%s | Total: Rs.%.2f%n", r, r.getPrice() * nights);
            }

            int roomNo = readInt("Enter room number to book: ");
            Room room = available.stream().filter(r -> r.getRoomNumber() == roomNo).findFirst().orElse(null);
            if (room == null) {
                System.out.println("Invalid room number or room not in the available list.");
                return;
            }

            String name = readNonEmpty("Guest name: ");
            String phone = readPhone();
            double total = nights * room.getPrice();
            System.out.printf("%nAmount payable: Rs.%.2f for %d night(s).%n", total, nights);

            System.out.println("Payment method: 1. Card  2. UPI  3. Cash (pay at hotel)");
            int pm = readInt("Choose: ");
            PaymentMethod method;
            String detail = "";
            switch (pm) {
                case 1:
                    method = PaymentMethod.CARD;
                    detail = readNonEmpty("Enter 16-digit card number: ").replace(" ", "");
                    break;
                case 2:
                    method = PaymentMethod.UPI;
                    detail = readNonEmpty("Enter UPI ID: ");
                    break;
                case 3:
                    method = PaymentMethod.CASH;
                    break;
                default:
                    System.out.println("Invalid payment method. Booking aborted.");
                    return;
            }

            Booking b = service.makeReservation(room, name, phone, dates[0], dates[1], method, detail);
            if (b != null) {
                System.out.println("\nBooking confirmed!");
                b.printDetails(room);
            } else {
                System.out.println("Booking was not completed.");
            }
        }

        private void viewBooking() {
            String id = readNonEmpty("Enter booking ID: ");
            Booking b = service.findBooking(id);
            if (b == null) {
                System.out.println("No booking found with ID: " + id);
                return;
            }
            b.printDetails(service.findRoom(b.getRoomNumber()));
        }

        private void viewMyBookings() {
            String phone = readNonEmpty("Enter phone number: ");
            List<Booking> list = service.findByPhone(phone);
            if (list.isEmpty()) {
                System.out.println("No bookings found for this phone number.");
                return;
            }
            printBookingTable(list);
        }

        private void cancelBooking() {
            String id = readNonEmpty("Enter booking ID to cancel: ");
            Booking b = service.findBooking(id);
            if (b == null) {
                System.out.println("No booking found with ID: " + id);
                return;
            }
            b.printDetails(service.findRoom(b.getRoomNumber()));
            if (b.getStatus() == BookingStatus.CANCELLED) return;
            System.out.printf("Refund if cancelled now: Rs.%.2f%n", service.calculateRefund(b));
            System.out.print("Confirm cancellation? (y/n): ");
            if (sc.nextLine().trim().equalsIgnoreCase("y")) {
                service.cancelReservation(id);
            } else {
                System.out.println("Cancellation aborted.");
            }
        }

        private void viewAllBookings() {
            if (service.getAllBookings().isEmpty()) {
                System.out.println("No bookings yet.");
                return;
            }
            printBookingTable(service.getAllBookings());
        }

        private void printBookingTable(List<Booking> list) {
            System.out.printf("%n%-8s %-6s %-15s %-12s %-12s %-10s %-10s%n",
                    "ID", "Room", "Guest", "Check-in", "Check-out", "Amount", "Status");
            for (Booking b : list) {
                System.out.printf("%-8s %-6d %-15s %-12s %-12s %-10.2f %-10s%n",
                        b.getBookingId(), b.getRoomNumber(), truncate(b.getGuestName(), 15),
                        b.getCheckIn(), b.getCheckOut(), b.getTotalAmount(), b.getStatus());
            }
        }

        // ------------------------- input helpers -------------------------

        private String truncate(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "."; }

        private int readInt(String prompt) {
            while (true) {
                System.out.print(prompt);
                try {
                    return Integer.parseInt(sc.nextLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println("Please enter a valid number.");
                }
            }
        }

        private String readNonEmpty(String prompt) {
            while (true) {
                System.out.print(prompt);
                String s = sc.nextLine().trim();
                if (!s.isEmpty()) return s;
                System.out.println("Input cannot be empty.");
            }
        }

        private String readPhone() {
            while (true) {
                String p = readNonEmpty("Phone number (10 digits): ");
                if (p.matches("\\d{10}")) return p;
                System.out.println("Invalid phone number.");
            }
        }

        private LocalDate readDate(String prompt) {
            while (true) {
                System.out.print(prompt);
                try {
                    return LocalDate.parse(sc.nextLine().trim());
                } catch (DateTimeParseException e) {
                    System.out.println("Invalid date. Use format YYYY-MM-DD.");
                }
            }
        }

        private LocalDate[] readDateRange() {
            LocalDate in = readDate("Check-in date (YYYY-MM-DD): ");
            LocalDate out = readDate("Check-out date (YYYY-MM-DD): ");
            if (in.isBefore(LocalDate.now())) {
                System.out.println("Check-in date cannot be in the past.");
                return null;
            }
            if (!out.isAfter(in)) {
                System.out.println("Check-out must be after check-in.");
                return null;
            }
            return new LocalDate[]{in, out};
        }

        private RoomCategory readCategory(boolean allowAll) {
            System.out.println("Category: 1. Standard  2. Deluxe  3. Suite" + (allowAll ? "  4. Any" : ""));
            while (true) {
                int c = readInt("Choose: ");
                switch (c) {
                    case 1: return RoomCategory.STANDARD;
                    case 2: return RoomCategory.DELUXE;
                    case 3: return RoomCategory.SUITE;
                    case 4: if (allowAll) return null;
                    default: System.out.println("Invalid choice.");
                }
            }
        }
    }

    // ============================== MAIN ===============================

    public static void main(String[] args) {
        FileStorage storage = new FileStorage("rooms.txt", "bookings.txt");
        HotelService service = new HotelService(storage);
        new ConsoleApp(service).run();
    }
}