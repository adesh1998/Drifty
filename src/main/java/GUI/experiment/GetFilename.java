package GUI.experiment;

import Enums.Program;
import GUI.Support.Job;
import Utils.Utility;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetFilename extends Task<ConcurrentLinkedDeque<Job>> {

    private final String link;
    private final String dir;
    private final String regex = "(\\[download] Downloading item \\d+ of )(\\d+)";
    private final Pattern pattern = Pattern.compile(regex);
    private final String lineFeed = System.lineSeparator();

    private int fileCount = 0;
    private int filesProcessed = 0;

    public GetFilename(String link, String dir) {
        this.link = link;
        this.dir = dir;
    }

    private final ConcurrentLinkedDeque<Job> jobList = new ConcurrentLinkedDeque<>();

    @Override
    protected ConcurrentLinkedDeque<Job> call() {
        updateProgress(0,1);
        this.updateMessage("Retrieving Filename");
        Thread thread = new Thread(getFileCount());
        result = -1;
        thread.start();
        while(result == -1) {
            sleep(500);
        }
        System.out.println("Threads Dead");
        boolean proceed = true;
        if(fileCount > 0) {
            System.out.println("Filecount: " + fileCount);
            AskYesNo ask = new AskYesNo("There are " + fileCount + " files in this list. Proceed and get all filenames?");
            proceed = ask.isYes();
        }
        if(proceed) {
            Timer timer = new Timer();
            timer.scheduleAtFixedRate(getJson(), 1000, 150);
            LinkedList<String> jsonList = Utility.getLinkMetadata(link);
            timer.cancel();
            sleep(500);//give timerTask enough time to do its last run
            jobList.clear();
            for(String json : jsonList) {
                String filename = Utility.getFilenameFromJson(json);
                String fileLink = Utility.getURLFromJson(json);
                String baseName = FilenameUtils.getBaseName(filename);
                filename = baseName + ".mp4";
                jobList.addLast(new Job(fileLink, dir, filename));
            }
            FormLogic.setColor(Constants.GREEN);
            updateMessage("File(s) added to batch.");
            updateProgress(0,1);
        }
        return jobList;
    }

    private TimerTask getJson() {
        File tempFolder = Paths.get(Program.get(Program.PATH), "Drifty").toFile();
        return new TimerTask() {
            @Override
            public void run() {
                ConcurrentLinkedDeque<File> deleteList = new ConcurrentLinkedDeque<>();
                ConcurrentLinkedDeque<Job> jobList = new ConcurrentLinkedDeque<>();
                File[] files = tempFolder.listFiles();
                for(File file : files) {
                    try {
                        String ext = FilenameUtils.getExtension(file.getAbsolutePath());
                        if(ext.equalsIgnoreCase("json")) {
                            String jsonString = FileUtils.readFileToString(file, Charset.defaultCharset());
                            String filename = Utility.getFilenameFromJson(jsonString);
                            String baseName = FilenameUtils.getBaseName(filename);
                            filename = baseName + ".mp4";
                            updateMessage("Found file: " + filename);
                            String link = Utility.getURLFromJson(jsonString);
                            jobList.addLast(new Job(link, dir, filename));
                            if(fileCount > 1){
                                filesProcessed++;
                                updateProgress(filesProcessed, fileCount);
                            }
                            FormLogic.addJob(jobList);
                            deleteList.addLast(file);
                        }
                    } catch (IOException ignored) {}
                }
                for(File file : deleteList) {
                    try {
                        FileUtils.forceDelete(file);
                    } catch (IOException ignored) {}
                }
            }
        };
    }

    private final StringProperty feedback = new SimpleStringProperty();

    private Runnable getFileCount() {
        return () -> {
            feedback.addListener(((observable, oldValue, newValue) -> {
                String[] list = newValue.split(lineFeed);
                 if(list.length > 3) {
                     for(String line : list) {
                         Matcher m = pattern.matcher(line);
                         int value;
                         if(m.find()) {
                             fileCount = Integer.parseInt(m.group(2));
                             break;
                         }
                     }
                 }
            }));
            try {
                String command = Program.get(Program.COMMAND);
                String[] args = new String[]{command, "--flat-playlist", "--skip-download", "-P", dir, link};
                ProcessBuilder pb = new ProcessBuilder(args);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                StringBuilder sbOutput = new StringBuilder();
                try {
                    try (InputStream inputStream = process.getInputStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (this.isCancelled()) {
                                System.out.println("Canceled");
                                break;
                            }
                            String newLine = new String(line);
                            sbOutput.append(newLine).append(lineFeed);
                            feedback.setValue(sbOutput.toString());
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                result = process.waitFor();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }
private int result = -1;
    private void sleep(long time) {
        try {
            TimeUnit.MILLISECONDS.sleep(time);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
