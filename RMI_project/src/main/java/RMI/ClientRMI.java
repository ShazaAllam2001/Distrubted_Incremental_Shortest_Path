package RMI;

import Util.RequestsGenerator;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.SimpleDateFormat;
import java.util.*;

public class ClientRMI {
    public static final int SLEEP_TIME = 4000;  // sleep for 4 sec
    private int ID;
    private final Registry registry;
    private final ServerI stub;
    private Logger logger;
    private Properties systemProps;
    private Properties generatorProps;
    private RequestsGenerator generator;

    public ClientRMI(int threadID) throws IOException, NotBoundException {
        System.out.println("Initializing Client");
        this.ID = threadID;
        this.registry = LocateRegistry.getRegistry("localhost", 1099);
        this.stub = (ServerI) registry.lookup("server");
        this.generatorProps = new Properties();
        generatorProps.load(new FileInputStream(ServerI.path + "Configs\\generator.properties"));
        float pWrite = Float.parseFloat(generatorProps.getProperty("pWrite"));
        int maxNodeID = Integer.parseInt(generatorProps.getProperty("maxNodeID"));
        this.generator = new RequestsGenerator(pWrite, maxNodeID);
        this.systemProps = new Properties();
        systemProps.load(new FileInputStream(ServerI.path + "Configs\\system.properties"));
        initLogger();
    }

    private void initLogger() {
        System.setProperty("name", "client");
        String dir = "clientLogs";
        System.setProperty("log.directory", dir);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss");
        System.setProperty("current.date.time", dateFormat.format(new Date()));
        System.setProperty("client.id", "_" + ID);

        File directory = new File(dir);
        if(!directory.exists()){
            directory.mkdir();
        }

        logger = LogManager.getLogger(ClientRMI.class);
        PropertyConfigurator.configure(ServerI.path + "Configs/logs-log4j.properties");
    }

    public void initializeGraph(String fileName) throws FileNotFoundException, RemoteException, InterruptedException {
        File input = new File(ServerI.path + "\\RMI\\" + fileName);
        Scanner scanner = new Scanner(input);
        String line;
        Queue<String> lines = new LinkedList<>();
        if(!stub.getInitialized()) {
            while((line = scanner.nextLine()) != null) {
                if(!stub.getInitialized() && line.equals("S")) {
                    System.out.print("Client " + ID + ": " + stub.buildGraph(lines, ID));
                    Thread.sleep(SLEEP_TIME);
                    break;
                }
                lines.add(line);
            }
        }
        scanner.close();
    }

    public void runFile(String fileName) throws FileNotFoundException, RemoteException, InterruptedException {
        File input = new File(ServerI.path + fileName);
        Scanner scanner = new Scanner(input);
        run(scanner);
    }

    public void runStdInput() throws RemoteException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        run(scanner);
    }

    private void run(Scanner scanner) throws RemoteException, InterruptedException {
        String line;
        Queue<String> lines = new LinkedList<>();
        while(true) {
            line = scanner.nextLine();
            if(line.equals("exit")) {
                break;
            }
            if(!stub.getInitialized() && line.equals("S")) {
                System.out.print("Client " + ID + ": " + stub.buildGraph(lines, ID));
                Thread.sleep(SLEEP_TIME);
                lines = new LinkedList<>();
            }
            if(stub.getInitialized() && line.equals("F")) {
                logger.info("Client " + ID + ": " + "requests sent: " + lines);
                long startTime = System.currentTimeMillis();
                List<String> results = stub.processBatch(lines, ID);
                long responseTime = System.currentTimeMillis() - startTime;
                for(String result : results) {
                    System.out.println("Client " + ID + ": " + result);
                }
                int numberOfClients = Integer.parseInt(systemProps.getProperty("GSP.numberOfNodes"));
                logger.info("Client " + ID + ": " + "response: " + results);
                logger.info("Client " + ID + ": " + "response time: " + responseTime + " ms");
                logger.info("Client " + ID + ": " + "number of requests per second: " + (lines.size()*numberOfClients)); // frequency of requests
                Thread.sleep(SLEEP_TIME);
                lines = new LinkedList<>();
            }
            lines.add(line);
        }
        scanner.close();
    }

    public void runAutoGeneratedBatches(int numOfBatches) throws RemoteException, InterruptedException {
        for(int i=0; i<numOfBatches; i++) {
            System.out.println("Client " + ID + ": Batch " + i);
            logger.info("Client " + ID + ": Batch " + i);
            runAutoGeneratedBatch();
        }
    }

    private void runAutoGeneratedBatch() throws RemoteException, InterruptedException {
        int requestsPerBatch = Integer.parseInt(generatorProps.getProperty("requestsPerBatch"));
        Queue<String> lines = generator.getNRequests(requestsPerBatch);
        if(stub.getInitialized()) {
            logger.info("Client " + ID + ": " + "requests sent: " + lines);
            long startTime = System.currentTimeMillis();
            List<String> results = stub.processBatch(lines, ID);
            //List<String> results = stub.processBatchUnparalleled(lines, ID);
            long responseTime = System.currentTimeMillis() - startTime;
            for(String result : results) {
                System.out.println("Client " + ID + ": " + result);
            }
            int numberOfClients = Integer.parseInt(systemProps.getProperty("GSP.numberOfNodes"));
            logger.info("Client " + ID + ": " + "response: " + results);
            logger.info("Client " + ID + ": " + "response time: " + responseTime + " ms");
            logger.info("Client " + ID + ": " + "number of requests per second: " + (lines.size()*numberOfClients)); // frequency of requests
            Thread.sleep(SLEEP_TIME);
        }
    }

}
