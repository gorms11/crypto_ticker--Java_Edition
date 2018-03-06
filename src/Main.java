import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;




class Main extends JFrame{

    private static final String DB_URL = "jdbc:sqlite:coins.db";  //used for connecting to SQLite database
    private static final String[] coin_type = {"LTC", "ETH", "XMR", "XVG", "XLM", "ZEC", "XRP", "REQ", "BCH", "LINK", "NXT", "BTC"};
    private static final String[] json_parsed_array = new String[coin_type.length];
    private static boolean json_parsed_array_write = false;
    private static boolean updated_values = false;
    private static String display_string = "grabbing data";  //string used to display data in the GUI


/*
 *  Constructor Main() is used to establish parameters and create an instance of the GUI.
 *  Currently, all displayed data is provided by a single string (display_string) which is bound to
 *  JLabel label1 on JPanel panel. An infinite loop is then used to continuously update the display.
 */
private Main() throws InterruptedException {
        this.setSize(1300,30);
        Toolkit tk = Toolkit.getDefaultToolkit();
        Dimension dim = tk.getScreenSize();
/*
 *      sets location of GUI on screen
 */     int xPos = (1920/2 - (1300/2));
        int yPos = (dim.height/2);
        this.setLocation(xPos, yPos);

        this.setVisible(true);

        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        this.setTitle("Crypto Ticker");

        JPanel panel = new JPanel();
/*
 *  JLabel label1 is bound to display_string, which is updated every ~30 seconds after the API is called
 */     JLabel label1 = new JLabel(display_string);
        panel.add(label1);
        this.add(panel);

/*
 *      Since display_string is changing every ~30 seconds, an infinite loop is used to update label1 every
 *      100 ms so changes to the GUI can be made
 */      while (true){
            label1.setText(display_string);
            Thread.sleep(100);
        }
    }

/*
 *  WriteToDB is responsible for the following functionality:
 *      1. Connecting an SQLite database and creating one if it does not already exist
 *      2. Creating the necessary tables for each coin if they do not already exist
 *      3. Parsing through the json data as a string
 *      4. Adding the parsed json data to the database
 */
    private static void WriteToDB(String[] data) throws SQLException {
        Statement stmt = null;
        Connection c = null;

/*
 *  All json data for for each coin parses the data between commas and is added to array coin_list.
 *  The number of parsed elements for a coin is usually around ~63 with a few exceptions up to ~74, so
 *  coin_list allocates 79 elements for the parsed data to be safe.
 */
        String[][] coin_list = new String[coin_type.length][79];


/*
 *  The following uses the SQLite JDBC Driver to interface with the SQL database.
 *  Note: the SQLite JDBC Driver is not included in the default java libraries.
 *  The .jar for the SQLite JDBC Driver can be found here:
 *  https://bitbucket.org/xerial/sqlite-jdbc/downloads/
 */
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection(DB_URL);

        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }

        stmt = c.createStatement();
        for (String coin : coin_type) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + coin + "('PRICE' INTEGER, 'LASTVOLUME' INTEGER, 'LASTVOLUMETO' INTEGER, 'VOLUMEDAY' INTEGER," +
                    "'VOLUMEDAYTO' INTEGER, 'VOLUME24HOUR' INTEGER, 'VOLUME24HOURTO' INTEGER, 'HIGH24HOUR' INTEGER, 'LOW24HOUR' INTEGER, 'MKTCAP' INTEGER, 'SUPPLY' INTEGER, 'TOTALVOLUME24H' INTEGER," +
                    "'TOTALVOLUME24HTO' INTEGER, 'LASTUPDATE' INTEGER)");
            System.out.println("wrote to database " + coin + " successfully");
        }

/*
 *  Parses the data between commas of the json string for each coin
 *  Note: coin_list[i] separates by coin and coin_list[i][j] separates by parsed data for its associated coin
 */     for (int i = 0; i < coin_type.length; i++) {
            String delims = "[,]";
            String[] tokens = data[i].split(delims);
            System.arraycopy(tokens, 0, coin_list[i], 0, tokens.length);
        }
/*
 *  Parses out unnecessary characters such as " } $ and then and then parses out char : and everything before it
 */     for (int i = 0; i < coin_list.length; i++) {
            for (int j = 0; j < coin_list[i].length; j++) {

                if (coin_list[i][j] != null) {
                    if (coin_list[i][j].contains("\"")) {
                        coin_list[i][j] = coin_list[i][j].replace("\"", "");
                    }

                    if (coin_list[i][j].contains("}")) {
                        coin_list[i][j] = coin_list[i][j].replace("}", "");
                    }

                    if (coin_list[i][j].contains("$ ")) {
                        coin_list[i][j] = coin_list[i][j].replace("$ ", "");
                    }
                    coin_list[i][j] = coin_list[i][j].substring(coin_list[i][j].lastIndexOf(":") + 1);
                   // System.out.println(coin_list[i][j]);
                }
            }
        }

/*
 *  Adds the parsed data for each coin to the appropriate column of its associated table in the database
 */     for (int i = 0; i < coin_type.length; i++){
            stmt.executeUpdate("INSERT INTO " + coin_type[i] + "(PRICE, LASTVOLUME, LASTVOLUMETO, VOLUMEDAY, VOLUMEDAYTO, VOLUME24HOUR, VOLUME24HOURTO, HIGH24HOUR, LOW24HOUR, MKTCAP, SUPPLY, TOTALVOLUME24H, TOTALVOLUME24HTO, LASTUPDATE)" +
                    "VALUES (" + coin_list[i][5] + ", " + coin_list[i][7] + ", " + coin_list[i][8] + ", " + coin_list[i][10] + ", " + coin_list[i][11] + ", " +  coin_list[i][12] + ", " + coin_list[i][13] + ", " + coin_list[i][18] + ", " +  coin_list[i][19] + ", " + coin_list[i][26] + ", " + coin_list[i][25] + ", " + coin_list[i][27] + ", " + coin_list[i][28] + ", " + coin_list[i][6] + ")");
        }
        stmt.close();
        c.close();
        System.out.println("done writing to database!");
    }

/*
 *  Grabs all the json data from the API and returns it as a string
 *  Note: String 'cur' is the 3-4 letter abbreviation for a specific coin
 */
    private static String GetAPI(String cur) throws IOException {
        StringBuilder json_string = new StringBuilder();
        URL url = new URL("https://min-api.cryptocompare.com/data/pricemultifull?fsyms=" + cur + "&tsyms=USD");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.connect();
        int responsecode = conn.getResponseCode();

        if (responsecode != 200) throw new RuntimeException("HttpResponseCode: " + responsecode);
        else {
            Scanner sc = new Scanner(url.openStream());
            while (sc.hasNext()) {
                json_string.append(sc.nextLine());
            }
            sc.close();
        }
        return json_string.toString();
    }


/*
 *  Parse and loop is an infinite loop that is responsible for the following:
 *      1. Calls GetAPI method to acquire json data for each coin and places it into String[] json_array
 *      2. Parses out the numeric fiat value of each coin and places it in String[] json_parsed_array
 *      3. Adds all parsed data to a single string (display_string) to display in GUI
 *      4. Calls WriteToDB to write json data from API to SQLite database if elapsed time >= 90 seconds
 */
    private static final Thread parse_and_loop = new Thread(() -> {
        long startTime = System.currentTimeMillis();
        //System.out.println(startTime);

        while (true){
            System.out.println("start loop!");
            String[] json_array = new String[coin_type.length];

/*
*  Grabs json data for each coin and places it into String[] json_array
*/                for (int i = 0; i < coin_type.length; i++) {
                try {
                    json_array[i] = GetAPI(coin_type[i]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
/*
*  Parses out the numeric fiat value of each coin and places it in String[] json_parsed_array
*  All data in the json_parsed_array is then added to String display_string
*/          json_parsed_array_write = true;
            display_string = "";
            StringBuilder display_string_build = new StringBuilder();
            for (int i = 0; i < coin_type.length; i++) {
                String delims = "[,]";
                String[] tokens = json_array[i].split(delims);
                String fiat_value = tokens[5].substring(tokens[5].lastIndexOf(":") + 1);
                if (fiat_value.contains("\"")) {
                    fiat_value = fiat_value.replace("\"", "");
                }
                json_parsed_array[i] = fiat_value;
                display_string_build.append(coin_type[i]).append(" : $").append(fiat_value).append("   ");
              //  System.out.println(coin_type[i] + " : " + fiat_value);
            }

            display_string = display_string_build.toString();

/*
*  Checks for elapsed time and calls WriteToDB for database write if elapsed time >= 90 seconds
*/             updated_values = true;
            json_parsed_array_write = false;
            try {
                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime >= 90000){
                    startTime = System.currentTimeMillis();
                    WriteToDB(json_array);
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
/*
*  Sleeps for 18 seconds before looping to conform with API request limits
*  Note: a single loop appears to take ~20-30 seconds
*/             try {
                Thread.currentThread().sleep(18000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    });

/*
 *  Simple thread for printing coin fiat values whenever new json data has been obtained
 *  Not really necessary, might delete soon
 */   private static final Thread printing_thread = new Thread(() -> {

     while(true){
         if (updated_values && !json_parsed_array_write){
             for (int i = 0; i < coin_type.length; i++){
                 System.out.println(coin_type[i] + " : $" + json_parsed_array[i]);
             }

             updated_values = false;
         }

         try {
             Thread.sleep(100);
         } catch (InterruptedException e) {
             e.printStackTrace();
         }
     }

 });

/*
 *  Starts threads and calls constructor to make GUI
 */   public static void main(String[] args) throws InterruptedException {
        parse_and_loop.start();
        printing_thread.start();
        new Main();
    }
}



