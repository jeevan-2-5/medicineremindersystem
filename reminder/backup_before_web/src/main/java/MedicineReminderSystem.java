import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;

import static spark.Spark.*;

public class MedicineReminderSystem {

    public static final String ACCOUNT_SID = "ACb38e8e3f256efb0b82208fbaef635f5a";
    public static final String AUTH_TOKEN = "8107677030326e2ccc7bf3df673936f9";
    public static final String FROM_NUMBER = "+18455529994";

    // Disable SLF4J messages and filter out any runtime stderr lines that contain "SLF4J:"
    static {
        // keep SLF4J simple logger properties off (may help some bindings)
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "off");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "false");
        System.setProperty("org.slf4j.simpleLogger.showLogName", "false");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
        System.setProperty("org.slf4j.impl.StaticLoggerBinder", "false");

        // Replace System.err with a PrintStream that suppresses lines containing "SLF4J:"
        try {
            final PrintStream originalErr = System.err;
            PrintStream filteringErr = new PrintStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    originalErr.write(b);
                }
            }) {
                @Override
                public void println(String s) {
                    if (s != null && s.contains("SLF4J:")) return;
                    super.println(s);
                }
                @Override
                public void print(String s) {
                    if (s != null && s.contains("SLF4J:")) return;
                    super.print(s);
                }
                @Override
                public void println(Object obj) {
                    if (obj != null && obj.toString().contains("SLF4J:")) return;
                    super.println(obj);
                }
            };
            System.setErr(filteringErr);
        } catch (Exception ignored) {
            // If filtering fails, fall back to default behaviour (best effort).
        }
    }

    static class MedicineReminder {
        String medicineName;
        String time;
        String patientNumber;
        String caretakerNumber;
        String riskMessage;

        MedicineReminder(String medicineName, String time, String patientNumber,
                         String caretakerNumber, String riskMessage) {
            this.medicineName = medicineName;
            this.time = time;
            this.patientNumber = patientNumber;
            this.caretakerNumber = caretakerNumber;
            this.riskMessage = riskMessage;
        }
    }

    static class ReminderQueue {

        private MedicineReminder[] arr;
        private int front, rear, size, capacity;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        ReminderQueue() {
            capacity = 5;
            arr = new MedicineReminder[capacity];
            front = rear = -1;
            size = 0;
        }

        private boolean isFull() { return size == capacity; }
        boolean isEmpty() { return size == 0; }

        private void resize() {
            int newCap = capacity * 2;
            MedicineReminder[] newArr = new MedicineReminder[newCap];

            int i = 0;
            int idx = front;
            while (i < size) {
                newArr[i] = arr[idx];
                idx = (idx + 1) % capacity;
                i++;
            }

            arr = newArr;
            capacity = newCap;
            front = 0;
            rear = size - 1;

            System.out.println("Queue resized. New Capacity: " + capacity);
        }

        synchronized void addReminder(MedicineReminder r) {
            if (isFull()) resize();

            if (isEmpty()) {
                front = rear = 0;
            } else {
                rear = (rear + 1) % capacity;
            }

            arr[rear] = r;
            size++;
            System.out.println("Added: " + r.medicineName + " at " + r.time);
        }

        synchronized void displayList() {
            System.out.println("\n=== Medicine List ===");

            if (isEmpty()) {
                System.out.println("Queue Empty.\n");
                return;
            }

            int idx = front;
            int count = 1;

            while (true) {
                MedicineReminder r = arr[idx];
                System.out.println(count + ". " + r.medicineName + " at " + r.time +
                        " (Patient: " + r.patientNumber + ")");
                if (idx == rear) break;
                idx = (idx + 1) % capacity;
                count++;
            }

            System.out.println("Total: " + size + " | Capacity: " + capacity);
        }

        int findIndexByName(String name) {
            if (isEmpty()) return -1;
            int idx = front;
            while (true) {
                if (arr[idx].medicineName.equalsIgnoreCase(name)) return idx;
                if (idx == rear) break;
                idx = (idx + 1) % capacity;
            }
            return -1;
        }

        synchronized boolean removeByName(String name) {
            int idx = findIndexByName(name);
            if (idx == -1) {
                System.out.println("Not found: " + name);
                return false;
            }
            removeByIndex(idx);
            System.out.println("Removed: " + name);
            return true;
        }

        synchronized void snoozeAtIndex(int idx, int mins) {
            if (idx == -1 || isEmpty()) return;

            MedicineReminder r = arr[idx];
            try {
                LocalTime t = LocalTime.parse(r.time, formatter);
                r.time = t.plusMinutes(mins).format(formatter);
                System.out.println("Snoozed '" + r.medicineName + "' → New time: " + r.time);
            } catch (Exception e) {
                System.out.println("Invalid time: " + r.time);
            }
        }

        synchronized void checkAndSend() {
            if (isEmpty()) return;

            LocalTime now = LocalTime.now();
            List<Integer> toRemove = new ArrayList<>();
            int idx = front;

            while (true) {
                MedicineReminder r = arr[idx];
                try {
                    LocalTime t = LocalTime.parse(r.time, formatter);

                    if (now.getHour() == t.getHour() && now.getMinute() == t.getMinute()) {

                        sendNotification(r);

                        Scanner sc = new Scanner(System.in);
                        System.out.print("Snooze '" + r.medicineName + "'? (yes/no): ");
                        String ans = sc.nextLine().trim().toLowerCase();

                        if (ans.equals("yes")) {
                            System.out.print("Minutes: ");
                            int mins = Integer.parseInt(sc.nextLine());
                            snoozeAtIndex(idx, mins);
                        } else {
                            toRemove.add(idx);
                        }
                    }

                } catch (Exception ignored) {}

                if (idx == rear) break;
                idx = (idx + 1) % capacity;
            }

            Collections.sort(toRemove, Collections.reverseOrder());
            for (int remIdx : toRemove) removeByIndex(remIdx);
        }

        private void removeByIndex(int idx) {
            int current = idx;
            while (current != rear) {
                int next = (current + 1) % capacity;
                arr[current] = arr[next];
                current = next;
            }
            arr[rear] = null;

            size--;
            rear = (rear - 1 + capacity) % capacity;

            if (size == 0) {
                front = rear = -1;
            }
        }

        void sendNotification(MedicineReminder r) {
            // SMS content: medicine and risk shown separately, with emojis
            String patientMsg =
                    "💊 Medicine: " + r.medicineName + "\n" +
                    "⚠️ Risk: " + r.riskMessage + "\n" +
                    "⏰ Time: " + r.time + "\n" +
                    "Please take it now.";

            String caretakerMsg =
                    "👩‍⚕️ Caregiver Alert\n" +
                    "Patient: " + r.patientNumber + "\n" +
                    "💊 Medicine: " + r.medicineName + "\n" +
                    "⚠️ Risk: " + r.riskMessage + "\n" +
                    "⏰ Time: " + r.time;

            // Send SMS to patient and caretaker
            sendSms(r.patientNumber, patientMsg);
            sendSms(r.caretakerNumber, caretakerMsg);

            // Clean console confirmation with emojis and separate fields
            System.out.println("✅ SMS sent → Medicine: " + r.medicineName +
                    " | Risk: " + r.riskMessage +
                    " | Patient: " + r.patientNumber +
                    " | Caretaker: " + r.caretakerNumber);
        }
    }

    public static void sendSms(String to, String msg) {
        try {
            Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
            Message.creator(
                    new com.twilio.type.PhoneNumber(to),
                    new com.twilio.type.PhoneNumber(FROM_NUMBER),
                    msg
            ).create();
            // Do not print low-level Twilio logs; only print high-level confirmation in sendNotification.
        } catch (Exception e) {
            // If SMS fails, show failure with emoji and message content (keeps user informed)
            System.out.println("❌ SMS FAILED → To " + to + ": " + msg);
        }
    }

    public static int safeInt(Scanner sc) {
        while (true) {
            try {
                return Integer.parseInt(sc.nextLine());
            } catch (Exception e) {
                System.out.println("Enter a valid number:");
            }
        }
    }

    public static void main(String[] args) {

        ReminderQueue rq = new ReminderQueue();
        Scanner sc = new Scanner(System.in);

        System.out.println("=== Hospital Medicine Reminder System ===");

        final Object reminderLock = new Object();
        final boolean[] reminderRunning = {false};

        while (true) {
            System.out.println("\n1. Add Medicine Reminder");
            System.out.println("2. Display Medicine List");
            System.out.println("3. Start Reminder System");
            System.out.println("4. Snooze Reminder");
            System.out.println("5. Remove Medicine");
            System.out.println("6. Exit");
            System.out.print("Choose: ");

            int choice = safeInt(sc);

            switch (choice) {
                case 1:
                    System.out.print("Medicine Name: ");
                    String name = sc.nextLine();

                    System.out.print("Time (HH:MM): ");
                    String time = sc.nextLine();

                    System.out.print("Patient Phone: ");
                    String patient = sc.nextLine();

                    System.out.print("Caretaker Phone: ");
                    String caretaker = sc.nextLine();

                    System.out.print("Risk Message: ");
                    String risk = sc.nextLine();

                    rq.addReminder(new MedicineReminder(name, time, patient, caretaker, risk));
                    break;

                case 2:
                    rq.displayList();
                    break;

                case 3:
                    synchronized (reminderLock) {
                        if (!reminderRunning[0]) {
                            reminderRunning[0] = true;
                            Thread t = new Thread(() -> {
                                System.out.println("\nReminder System Running...");
                                while (true) {
                                    rq.checkAndSend();
                                    try { Thread.sleep(10000); } catch (InterruptedException ignored) {}
                                }
                            });
                            t.setDaemon(true);
                            t.start();
                        } else {
                            System.out.println("Already running.");
                        }
                    }
                    break;

                case 4:
                    rq.displayList();
                    if (rq.isEmpty()) break;

                    System.out.print("Medicine name to snooze: ");
                    String sname = sc.nextLine();
                    int idx = rq.findIndexByName(sname);

                    if (idx == -1) {
                        System.out.println("Not found.");
                        break;
                    }

                    System.out.print("Minutes to snooze: ");
                    int mins = safeInt(sc);
                    rq.snoozeAtIndex(idx, mins);
                    break;

                case 5:
                    rq.displayList();
                    if (rq.isEmpty()) break;

                    System.out.print("Medicine name to remove: ");
                    String rem = sc.nextLine();
                    rq.removeByName(rem);
                    break;

                case 6:
                    System.out.println("Exiting...");
                    System.exit(0);
            }
        }
    }
}
