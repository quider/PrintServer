package pl.adriankozlowski;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.awt.print.PrinterJob;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Main {
    public static void main(String args[]) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new RootHandler());
        server.createContext("/print", new PrintHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    /**
     * Reads from file in resources html code and prints it to browser. Furthermore changes some text
     * which is responsible for dropdown with printers
     *
     * @param t {@link HttpExchange}
     * @throws IOException
     */
    private static void showForm(HttpExchange t) throws IOException {
        BufferedInputStream content = (BufferedInputStream) Main.class.getResource("/index.html").getContent();
        byte[] bytes = content.readAllBytes();
        String page = new String(bytes);
        page = page.replace("${options}", prepareHtmlListOfPrinters());
        bytes = page.getBytes();
        t.sendResponseHeaders(200, bytes.length);
        try (BufferedOutputStream out = new BufferedOutputStream(t.getResponseBody())) {
            byte[] buffer = new byte[1024];
            out.write(bytes, 0, bytes.length);
        }
    }

    /**
     * Method directly changes ${options} string in index.html file to <option></option> tags with printers
     * which are available in system
     *
     * @return
     */
    private static CharSequence prepareHtmlListOfPrinters() {
        StringBuilder options = new StringBuilder();
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService printService : printServices) {
            options.append("<option value=\"").append(printService.getName()).append("\">").append(printService.getName()).append("</option>\n");
        }
        return options.toString();
    }


    static class RootHandler implements HttpHandler {

        public void handle(HttpExchange t) {
            try {
                showForm(t);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


    }

    /**
     * Returns printer service by string name of printer
     *
     * @param printerName printer name (taken from form
     * @return PrinterSe
     */
    private static PrintService findPrintService(String printerName) {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService printService : printServices) {
            if (printService.getName().trim().equals(printerName)) {
                return printService;
            }
        }
        return null;
    }

    private static class PrintHandler implements HttpHandler {

        @Override
        public synchronized void handle(HttpExchange exchange) throws IOException {
            try {
                PrintService myPrintService = null;
                InputStream requestBody = exchange.getRequestBody();
                byte[] bytes = requestBody.readAllBytes();
                Path pathToPrintableFile = makeRoomForNewFile();
                Path file = createFileFromStream(bytes, pathToPrintableFile);
                PDDocument document = PDDocument.load(new File(file.toUri()));
                myPrintService = readPrintServiceFromForm(myPrintService, bytes);
                PrinterJob job = PrinterJob.getPrinterJob();
                job.setPageable(new PDFPageable(document));
                job.setPrintService(myPrintService);
                job.print();
                showForm(exchange);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * Pareses form to read exactly one field which is "printer" form dropdown
         * @param myPrintService printer service to be set
         * @param bytes all response body in array of bytes
         * @return
         * @throws IOException
         */
        private PrintService readPrintServiceFromForm(PrintService myPrintService, byte[] bytes) throws IOException {
            ByteArrayInputStream is = new ByteArrayInputStream(bytes);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));
            boolean read = true;
            while (read) {
                String line = bufferedReader.readLine();
                if (line.equals("Content-Disposition: form-data; name=\"printer\"")) {
                    bufferedReader.readLine();
                    String printer = bufferedReader.readLine();
                    myPrintService = findPrintService(printer);
                    read = false;
                    break;
                } else {
                    continue;
                }

            }
            return myPrintService;
        }

        private Path createFileFromStream(byte[] bytes, Path fileToPrint) throws IOException {
            return Files.write(fileToPrint, bytes, StandardOpenOption.CREATE_NEW);
        }

        private Path makeRoomForNewFile() throws IOException {
            Path fileToPrint = Paths.get("fileToPrint.pdf");
            if (Files.exists(fileToPrint)) {
                Files.delete(fileToPrint);
            }
            return fileToPrint;
        }
    }
}
