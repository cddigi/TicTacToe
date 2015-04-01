
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;


/*
 protocol commands

 exit   --- triggerred by exit menu item on Game menu
 playagain  query --- triggerred by playagain menu item on Game menu, available 
                       only when the game is over.
 playagain deny  --- sent in response to a playagain query message when the recipient
                     refused to play another game.
playagain consent  --- sent in response to a playagain query message when the recipient
                     agrees to play another game.
 move row col   --- triggerred by a move to the specified cell
 chat message   --- triggerred by a click on the send message button.   
*/

// Objects for reading and writing  the network connections.
class NetComm
{
    public BufferedReader reader;
    public PrintWriter writer;
}


// This class gatheres together various variables that 
// keep track of the state of the game.
class GameState
{
   public  int numberOfCellsFilled = 0;
   public boolean localPlayerTurn;
   public boolean localPlayerGoesFirst; 
   public boolean gameOver;
   public boolean [][] cellIsFilled = new boolean[3][3];
   
   // resets these variables at the beginning of new game.
   public void reset()
   {
       numberOfCellsFilled = 0;
       localPlayerGoesFirst = !localPlayerGoesFirst;
       localPlayerTurn = localPlayerGoesFirst;
       gameOver = false;
       
       for (int r = 0; r < 3; r++)
           for (int c = 0; c < 3; c++)
               cellIsFilled[r][c] = false;
   }
}


// This interface gatheres together various variables needed by different
// parts of the program while the game is in progress.
interface GameSharedVariables
{
   // Game menu stuff
   public Menu gameMenu = new Menu("Game");
   public MenuItem playAgainMenuItem = new MenuItem("Play Again");
   public MenuItem exitMenuItem = new MenuItem("Exit");       
 
   // Other GUI components that need to be shared
   public  TextField statusBar = new TextField();
   public  GridPane tttBoard = new GridPane();
   public  TextArea chatHistoryTArea = new TextArea();
   public  TextField sendMessageTF = new TextField();   
   public  TextField[][] cells = new TextField[3][3];  
   
   // This shared netComm object will be used to read and write the 
   // network connections. The reader and writer in this
   // object need to be initialized after the sockets are 
   // connected.  
   public  NetComm netComm = new NetComm();
   
   // The localPlayerId and remotePlayerId string builders need 
   // to be initialized to "X" for the server and "O" for the client.
   public StringBuilder localPlayerId = new StringBuilder();
   public StringBuilder remotePlayerId = new StringBuilder();    
   // These default methods just return the strings from the string builders.
   default String getLocalPlayerId() { return localPlayerId.toString();}
   default String getRemotePlayerId() { return remotePlayerId.toString();}  
   
   public GameState gameState = new GameState();
   
    // This methods is used to reset shared variables to restart the game.
    default void reset()
    { 
        // reset cells
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                cells[r][c].setText(" ");
       
        // reset gameState
        gameState.reset();
        if (gameState.localPlayerGoesFirst)
            statusBar.setText("Make a move.");
        else
            statusBar.setText("Wait for your turn.");
    }
   
   // Check if the player has won
   default boolean hasWon(String playerId)
   {
       for(int r = 0; r < 3; r++)
           if(hasWonRow(r, playerId)) return true;
       for(int c = 0; c < 3; c++)
           if(hasWonColumn(c, playerId)) return true;
       if(hasWonRisingDiag(playerId)) return true;
       if(hasWonFallingDiag(playerId)) return true;
       
       return false;
   }
   // Check if the player has won a row
   default boolean hasWonRow(int r, String playerId)
    {
       boolean won = true;
       for(int c = 0; c < 3; c++)
           if(!cells[r][c].getText().equals(playerId))
               won = false;
       return won;
    }
   
   // Check if the player has won column.
   default boolean hasWonColumn(int c, String playerId)
   {
        boolean won = true;
        for(int r = 0; r < 3; r++)
            if(!cells[r][c].getText().equals(playerId))
               won = false;
        return won;
   }
   
   // Check if the player has won the rising diagonal.
   default boolean hasWonRisingDiag(String playerId)
    {
        boolean won = true;
        if(!cells[2][0].getText().equals(playerId)) won = false;
        if(!cells[1][1].getText().equals(playerId)) won = false;
        if(!cells[0][2].getText().equals(playerId)) won = false;
        return won;
    }
   
   // Check if the player has won the falling diagonal.
   default boolean hasWonFallingDiag(String playerId)
   {
        boolean won = true;
        if(!cells[0][0].getText().equals(playerId)) won = false;
        if(!cells[1][1].getText().equals(playerId)) won = false;
        if(!cells[2][2].getText().equals(playerId)) won = false;
        return won;
   }           
}

// This class is the main JavaFX application. It builds the user interface
// and attaches the event handlers.
public class TicTacToe extends Application implements GameSharedVariables
{
    @Override
    public void start(Stage stage) throws Exception
    {        
       MenuBar menuBar = new MenuBar(); 
       
       // Network role menu
       Menu roleMenu = new Menu("Networking Role");
       MenuItem serverMenuItem = new MenuItem("Server");
       MenuItem clientMenuItem = new MenuItem("Client...");     
       roleMenu.getItems().addAll(serverMenuItem, clientMenuItem);  
       
       // Game Menu       
       gameMenu.getItems().addAll(playAgainMenuItem, exitMenuItem);  
       gameMenu.setDisable(true);
       
       
       menuBar.getMenus().addAll(roleMenu, gameMenu);
       BorderPane outerPane = new BorderPane();
       outerPane.setTop(menuBar);
      
       outerPane.setBottom(statusBar);
       
       statusBar.setEditable(false);
       for (int r = 0; r < 3; r++)
       {
           for (int c = 0; c < 3; c++)
           {
               cells[r][c] = new TextField();
               cells[r][c].setEditable(false);
               cells[r][c].setFont(new Font(32));
               tttBoard.add(cells[r][c], c, r);
               cells[r][c].setPrefColumnCount(1);
           }
       } 
       GameSharedVariables.tttBoard.setGridLinesVisible(true);
       
       HBox centerHBox = new HBox(30);
       centerHBox.getChildren().addAll(tttBoard, 
                                       chatHistoryTArea);
       
       VBox centerVBox = new VBox(10);
       Button sendMessageButton = new Button("Send Message");
       HBox sendMessageButtonHBox = new HBox();
       sendMessageButtonHBox.setAlignment(Pos.CENTER_RIGHT);
       sendMessageButtonHBox.getChildren().add(sendMessageButton);
       centerVBox.getChildren().addAll(centerHBox, 
                            new Label("Type Message to send:"),
                            sendMessageTF,
                            sendMessageButtonHBox);
       
       outerPane.setCenter(centerVBox);
       centerVBox.setPadding(new Insets(10));
       
       // Install the handlers on the Network Role menu items.
       serverMenuItem.setOnAction(new ServerSelectHandler());
       clientMenuItem.setOnAction(new ClientSelectHandler());
       
       // Install a handler on each of the Game menu items
       exitMenuItem.setOnAction(new GameExitHandler());
       playAgainMenuItem.setOnAction(new GamePlayAgainHandler());
       
       // Install a handler on all the TTT cells
       EventHandler<MouseEvent> tttCellHandler = new CellClickHandler();
       for (int r = 0; r < 3; r++)
           for (int c = 0; c < 3; c++)
               cells[r][c].setOnMouseClicked(tttCellHandler);
       
       // Install a handler on the SendMessage Button
       sendMessageButton.setOnAction(new SendMessageButtonHandler());
       
       stage.setScene(new Scene(outerPane));
       stage.setTitle("Tic Tac Toe");
       stage.show();       
    }   
    
    public static void main(String [] args)
    {
        launch(args);
    }
}

// This is the handler for the tic tac toe cells, when clicked on 
// with a mouse. Handles interaction with the local player.
// Make sure the local player does not play out of turn, or move 
// into an occupied cell. Update the status bar as needed to inform the 
// user if not their turn, or trying to make an illegal move.
// Send notificaiton of local move to remote side, and check for
// game win, game loss, or game tie and update status bar.
// Update game state as needed after a local move.
class CellClickHandler implements EventHandler<MouseEvent>, GameSharedVariables
{  
    @Override
    public void handle(MouseEvent event)
    {
       if(gameState.localPlayerTurn)
       {
           TextField TF = (TextField)event.getSource();
           int r = GridPane.getRowIndex(TF);
           int c = GridPane.getColumnIndex(TF);
           if(gameState.cellIsFilled[r][c])
               InformationBox.showDialog("Tic Tac Toe", "Invalid Move");
           else if(gameState.gameOver)
               InformationBox.showDialog("Tic Tac Tow", "The Game is Over");
           else
           {
                cells[r][c].setText(localPlayerId.toString());
                gameState.cellIsFilled[r][c] = true;
                gameState.localPlayerTurn = false;
                gameState.numberOfCellsFilled++;
                statusBar.setText("Please wait for opponent to move.");
                netComm.writer.println("move " + r + " " + c);
                if(hasWon(localPlayerId.toString()))
                {
                    gameState.gameOver = true;
                    playAgainMenuItem.setDisable(false);
                    InformationBox.showDialog("Tic Tac Toe", "You have won!");
                }
                else if(gameState.numberOfCellsFilled == 9)
                {
                    gameState.gameOver = true;
                    playAgainMenuItem.setDisable(false);
                    InformationBox.showDialog("Tic Tac Toe", "Cats Game!");
                }
           }
       }
       else           
           InformationBox.showDialog("Tic Tac Toe", "Please wait for your turn.");
    }   
}

/*
  This class is used to create a separate thread to monitor
  the incoming network connection and respond to protocol commands.
*/
class RemoteInputHandler implements Runnable, GameSharedVariables
{  
     @Override
    public void run()
    {
         try
         {
             String input = netComm.reader.readLine();
             while (input != null)
             {
                // Make a separate copy of the input string 
                String inputCopy = input;
                // Post a work order to process the command on the GUI thread
                Platform.runLater( () -> {handleRemote(inputCopy);});
                // Get the next remote input
                input = netComm.reader.readLine();
             }
         } catch (IOException ex)
         {
             throw new RuntimeException(ex);
         }
        
    }
    // Will be posted to the GUI thread to update GUI and program variables.
    // Make sure all received protocol messaages are handled appropriately
    // and that local GUI.  game state, and status bar are correctly updated.
    private void handleRemote(String cmd)
    {
        Scanner sc = new Scanner(cmd);
        String opcode = sc.next();
        switch(opcode)
        {
            case "chat" :
                String msg = "";
                while(sc.hasNext())
                {
                    msg = msg.concat(sc.next() + " ");
                }
                chatHistoryTArea.appendText("Opponent: " + msg.trim() + "\n");
                break;
            case "move" :
                int r = sc.nextInt();
                int c = sc.nextInt();
                processMove(r, c);                
                break;
            case "exit" : 
                InformationBox.showDialog("Tic Tac Toe", "Opponent has quit.");
                Platform.exit();
                break;
            case "playagain" :
                String playcode = sc.next();
                switch(playcode)
                {
                    case "query" :
                        MessageBox ask = new MessageBox("Tic Tac Toe", "Would you like to play again?");
                        ask.showAndWait();
                        if(ask.returnValue == 0) netComm.writer.println("playagain deny");
                        else 
                        {
                            reset();
                            netComm.writer.println("playagain consent");
                        }
                        break;
                    case "consent" :
                        MessageBox ask1 = new MessageBox("Tic Tac Toe", "Opponent wants to play again.");
                        ask1.showAndWait();
                        if(ask1.returnValue == 1) reset();
                        else
                        {
                            netComm.writer.println("playagain deny");
                            Platform.exit();
                        }
                        break;
                    case "deny" :
                        InformationBox.showDialog("Tic Tac Toe", "Opponent has quit.");
                        Platform.exit();
                        break;
                }
               
            default:            
        }
    } 
   
    // Used to process a protocol move row col command.
    // Must do all appropriate updates of game state
    // and GUI variables, including status bar.
    private void processMove(int row, int col)
    {
        cells[row][col].setText(remotePlayerId.toString());
        gameState.cellIsFilled[row][col] = true;
        gameState.localPlayerTurn = true;
        gameState.numberOfCellsFilled++;
        if(hasWon(remotePlayerId.toString()))
        {    
            gameState.gameOver = true;
            playAgainMenuItem.setDisable(false);
            InformationBox.showDialog("Tic Tac Toe", "You have lost!");
        }
        else if(gameState.numberOfCellsFilled == 9)
        {
            gameState.gameOver = true;
            playAgainMenuItem.setDisable(false);
            InformationBox.showDialog("Tic Tac Toe", "Cats Game!");
        }
    }
}

// This code is given as an example.
class ServerSelectHandler implements EventHandler<ActionEvent>, GameSharedVariables
{
    @Override
    public void handle(ActionEvent event)
    {
        // This is the server.
        // server is "X" and client is "O".
        localPlayerId.append("X");
        remotePlayerId.append("O");
        statusBar.setText("Server role selected");
        gameState.localPlayerTurn = true; // Server goes first the first time
        gameState.numberOfCellsFilled = 0;
        gameState.localPlayerGoesFirst = true;
       
        try
        {
            // Set up server socket
            ServerSocket serverSock  = new ServerSocket(50000);
            System.err.println("Created the server socket.");
            statusBar.setText("Please wait for a client to connect.");
            Socket sock =  serverSock.accept();
            statusBar.setText("Client has connected. Make a move.");
            netComm.reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            netComm.writer = new PrintWriter(sock.getOutputStream(), true);
            
            // Set up a thread to monitor the incoming connection
            new Thread(new RemoteInputHandler()).start();
        } catch (IOException ex)
        {
            throw new RuntimeException(ex);
        }
        gameMenu.setDisable(false);   
        playAgainMenuItem.setDisable(true);
    }    
}

// This handles the client connection. 
class ClientSelectHandler implements EventHandler<ActionEvent>, GameSharedVariables
{
    @Override
    public void handle(ActionEvent event)
    {
       // This is the client.
        // server is "X" and client is "O".
        localPlayerId.append("O");
        remotePlayerId.append("X");
        statusBar.setText("Client role selected");
        gameState.localPlayerTurn = false; // Server goes first the first time
        gameState.numberOfCellsFilled = 0;
        gameState.localPlayerGoesFirst = false;
       
        try
        {
            // Set up client socket
            String ip = "localhost";
            MyInputPane IP = new MyInputPane("IP", "Enter IP of server: ", "localhost");
            ip = IP.showDialog();
            Socket sock  = new Socket(ip, 50000);
            System.err.println("Created the client socket.");
            statusBar.setText("Connected to the server.");
            statusBar.setText("Please wait for the server to make a move.");
            netComm.reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            netComm.writer = new PrintWriter(sock.getOutputStream(), true);
            
            // Set up a thread to monitor the incoming connection
            new Thread(new RemoteInputHandler()).start();
        } catch (IOException ex)
        {
            throw new RuntimeException(ex);
        }
        gameMenu.setDisable(false);   
        playAgainMenuItem.setDisable(true);
    }    
}

// Handles sending of chat messages to remote side, and also updating the 
// local chatHistory text area.
class SendMessageButtonHandler implements EventHandler<ActionEvent>, GameSharedVariables
{
    @Override
    public void handle(ActionEvent event)
    {
        String msg = sendMessageTF.getText();
        sendMessageTF.clear();
        chatHistoryTArea.appendText("You: " + msg + "\n");
        netComm.writer.println("chat " + msg);
    }    
}

// This is the hander for the game menu exit menuitem.
class GameExitHandler implements EventHandler<ActionEvent>, GameSharedVariables
{
    @Override
    public void handle(ActionEvent event)
    {
       netComm.writer.println("exit");
       Platform.exit();
    }    
}

// This is the hander for the game menu play again menuitem.
class GamePlayAgainHandler implements EventHandler<ActionEvent>, GameSharedVariables
{
    @Override
    public void handle(ActionEvent event)
    {
       netComm.writer.println("playagain query");
    }    
}

// Message Box dialog to show a message and get a yes no reply. 
// Call the static method to use this dialog, and check if the 
// returned value is MessageBox.YES or MessagBox.NO.
class MessageBox extends Stage
{
    public static final int YES = 1;
    public static  final int NO = 0;
    int returnValue;
    public MessageBox(String title, String message)
    {
        VBox vBox = new VBox(10);
        vBox.setPadding(new Insets(10));
        Label promptLabel = new Label(message);
      
        
        Button yesButton = new Button("Yes");
        Button noButton = new Button("No");
        
        HBox iPHBox = new HBox(10);
        iPHBox.getChildren().addAll(promptLabel);
        
        HBox hBox = new HBox(10);
        hBox.getChildren().addAll(yesButton, noButton);
        hBox.setAlignment(Pos.CENTER);
        vBox.getChildren().addAll(iPHBox, hBox);
        
        Scene scene = new Scene(vBox);
        this.setScene(scene); 
        this.setTitle(title);
        
        this.initModality(Modality.WINDOW_MODAL);
        
        yesButton.setOnAction(evt -> 
                 {     
                     returnValue = YES;
                     this.hide();                    
                 });
        noButton.setOnAction(evt -> 
                 {     
                     returnValue = NO;
                     this.hide();                    
                 });        
    }
    
    public static int showDialog(String title, String message)
    {
        MessageBox mBox = new MessageBox(title, message);
        mBox.showAndWait();     
        return mBox.returnValue;
    }
}

// Information Box to display a message to the user.
// Always returns InformationBox.OK.
class InformationBox extends Stage
{
    public static final int OK = 1;  
    int returnValue;
    public InformationBox(String title, String message)
    {
        VBox vBox = new VBox(10);
        vBox.setPadding(new Insets(10));
        Label promptLabel = new Label(message);          
        Button oKButton = new Button("OK");        
        HBox iPHBox = new HBox(10);
        iPHBox.setAlignment(Pos.CENTER);
        iPHBox.getChildren().addAll(promptLabel);
        
        HBox hBox = new HBox(10);
        hBox.getChildren().addAll(oKButton);
        hBox.setAlignment(Pos.CENTER);
        vBox.getChildren().addAll(iPHBox, hBox);
        
        Scene scene = new Scene(vBox);
        this.setScene(scene); 
        this.setTitle(title);
        
        this.initModality(Modality.WINDOW_MODAL);
        
        oKButton.setOnAction(evt -> 
                 {     
                     returnValue = OK;
                     this.hide();                    
                 });            
    }
    
    public static int showDialog(String title, String message)
    {
        InformationBox mBox = new InformationBox(title, message);
        mBox.showAndWait();     
        return mBox.returnValue;
    }
}


/**
 * Dialog box to get text input from the user.
 * Use one of the two constructors to create an
 * MyInputPane object and call its instance
 * method showDialog(). Returns the string entered in 
 * the text field.
 * @author gcm
 */
class MyInputPane extends Stage
{
    private String input;
    public MyInputPane(String title, String prompt)
    {
        this(title, prompt, "");
    }
    /**
     * 
     * @param title
     * @param prompt
     * @param defaultInput 
     */
    public MyInputPane(String title, String prompt, String defaultInput)
    {
        VBox vBox = new VBox(10);
        vBox.setPadding(new Insets(10));
        Label promptLabel = new Label(prompt);
        TextField tF = new TextField(defaultInput);
        tF.setPrefColumnCount(12);
        
        Button oKButton = new Button("Ok");
        
        HBox iPHBox = new HBox(10);
        iPHBox.getChildren().addAll(promptLabel, tF);
        
        HBox hBox = new HBox(10);
        hBox.getChildren().add(oKButton);
        hBox.setAlignment(Pos.CENTER_RIGHT);
        vBox.getChildren().addAll(iPHBox, hBox);
        
        Scene scene = new Scene(vBox);
        this.setScene(scene); 
        this.setTitle(title);
        
        this.initModality(Modality.WINDOW_MODAL);
        
        oKButton.setOnAction(evt -> 
                 {
                     input = tF.getText();
                     this.hide();                    
                 });
    }
    
    public String showDialog()
    {
        this.showAndWait();
        return input;
    }
}