import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class Client extends JFrame implements Runnable
{
   private JTextField idField; // textfield to display player's mark
   private JTextArea displayArea; // JTextArea to display output
   private JPanel boardPanel; // panel for tic-tac-toe board
   private JPanel panel2; // panel to hold board
   private ArrayList<Square> board; // tic-tac-toe board
   private Square currentSquare; // current square
   private Socket connection; // connection to server
   private Scanner input; // input from server
   private Formatter output; // output to server
   private String ticTacToeHost; // host name for server
   private String myMark; // this client's mark
   private boolean myTurn; // determines which client's turn it is
   private final String X_MARK = "X"; // mark for first client
   private final String O_MARK = "O"; // mark for second client
   private ArrayList<String> waitingGames = new ArrayList<>();
      
   // set up user-interface and board
   public Client( String host )
   { 
      ticTacToeHost = host; // set name of server
      displayArea = new JTextArea( 4, 30 ); // set up JTextArea
      displayArea.setEditable( false );
      add( new JScrollPane( displayArea ), BorderLayout.SOUTH );

      boardPanel = new JPanel(); // set up panel for squares in board
      boardPanel.setLayout( new GridLayout( 3, 3, 0, 0 ) );

      board = new ArrayList<>(); // create board
      
      
      // create the game squares
      for ( int i = 0; i <= 9 ; i++ ) 
      {
    	  	Square newSquare = new Square( " ", i );
            board.add(newSquare);
            if(i>0)
            	boardPanel.add( newSquare );
      } 
      

      idField = new JTextField(); // set up textfield
      idField.setEditable( false );
      add( idField, BorderLayout.NORTH );
      
      panel2 = new JPanel(); // set up panel to contain boardPanel
      panel2.add( boardPanel, BorderLayout.CENTER ); // add board panel
      add( panel2, BorderLayout.CENTER ); // add container panel

      setSize( 300, 225 ); // set size of window
      setVisible( true ); // show window
      startClient();
   } 

   // start the client thread
   public void startClient()
   {
      try // connect to server and get streams
      {
         // make connection to server
         connection = new Socket(InetAddress.getByName( ticTacToeHost ), 12345);
          // get streams for input and output
         input = new Scanner( connection.getInputStream() );
         output = new Formatter( connection.getOutputStream() );
      } // end try
      catch ( IOException ioException )
      {
         ioException.printStackTrace();         
      } // end catch

      // create and start worker thread for this client
      ExecutorService worker = Executors.newFixedThreadPool( 2 );
      worker.execute(this); // execute client
      
   } 

   // control thread that allows continuous update of displayArea
   public void run()
   {
	   chooseName("user");
	   processMessage(input.nextLine() );
	   if(input.nextLine().equals("Sending Games"))
	   {
		   getWaitingGames();
	   }
	   
	  
	   myMark = input.nextLine(); // get player's mark (X or O)

       SwingUtilities.invokeLater( 
         new Runnable() 
         {         
            public void run()
            {
               // display player's mark
               idField.setText( "You are player \"" + myMark + "\"" );
            } 
         } 
      ); 
         
      myTurn = ( myMark.equals( X_MARK ) ); // determine if client's turn

      // receive messages sent to client and output them
      while ( true )
      {
         if ( input.hasNextLine() )
            processMessage( input.nextLine() );
      } // end while
   } // end method run

   private void getWaitingGames(){
	
	   waitingGames.clear();
	   String game = input.nextLine();
	   while(!game.equals("Finished Sending Games"))
	   {		   
		   waitingGames.add(game);
		   game = input.nextLine();
	   }	   
	   
	   String[] list;
	   
	   if(waitingGames.size()>0)
	   {
		   list = new String[waitingGames.size()+1];
		   waitingGames.toArray(list);
	   } 
	   else 
	   {
		   list = new String[1];
	   }
	   
	   list[list.length-1]= "Create New Game";
	   String gameChoice = (String) JOptionPane.showInputDialog(this,
				"join to, or create new game: ","Select", JOptionPane.INFORMATION_MESSAGE, 
				null, list,list[list.length-1]);
	   if(gameChoice == null)
	   { 
		   return;
	   }
	   output.format("%s\n", gameChoice);
	   output.flush();
	   if(gameChoice.equals("Create New Game")){
		   chooseName("Game");
	   }
	   

   }
   
   
   private void chooseName(String type){
	   String[] options = {"OK"};
	   JPanel panel = new JPanel();
	   JLabel lbl = new JLabel("Enter name: ");
	   JTextField txt = new JTextField(10);
	   panel.add(lbl);
	   panel.add(txt);
	   JOptionPane.showOptionDialog(this, panel, ("Enter "+ type+ " name"), JOptionPane.NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options , options[0]);
	   String text = txt.getText();
	   output.format(text+"\n");
	   output.flush();
   }
   // process messages received by client
   private void processMessage( String message )
   {
      // valid move occurred
      if ( message.equals( "Valid move." ) ) 
      {
         displayMessage( "Valid move, please wait.\n" );
         setMark( currentSquare, myMark ); // set mark in square
      } // end if
      else if ( message.equals( "Invalid move, try again" ) ) 
      {
         displayMessage( message + "\n" ); // display invalid move
         myTurn = true; // still this client's turn
      } // end else if
      else if ( message.equals( "Opponent moved" ) || message.contains( "Your turn" ) ) 
      {
         if(message.equals( "Opponent moved" )){
        	 int location = input.nextInt(); // get move location
        	 input.nextLine(); // skip newline after int location
        	 setMark(  board.get(location), 
            ( myMark.equals( X_MARK ) ? O_MARK : X_MARK ) ); // mark move 
         }
    	                                
         displayMessage( "Your turn.\n" );
         myTurn = true; // now this client's turn
      } 
      else if (message.equals("You win the game!") || message.contains("wins") || message.equals("Your opponent has left") )
      {
    	myTurn = false; 
    	displayMessage( message + "\n" );
    	   	
       String[] options = {"Yes", "No"};
   	   JPanel panel = new JPanel();
   	   JLabel lbl = new JLabel("Do you want to continue playing");
   	   panel.add(lbl);
   	   String text;
   	   int choice = JOptionPane.showOptionDialog(this, panel, ("Do you want to continue playing?"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options , options[0]);
   	   
   	   if(choice == 0)
   	   {   		     		   
   		   text = "Continue Playing";
   		   output.format("%s\n", "End Game");
   		   output.format("%s\n","Continue Playing");
   	   }
   	   else
   	   {
   		   text = "Stop Playing";
   		   output.format("%s\n", "End Game");
   		   output.format("%s\n","Stop Playing");
   	   }
   	   
   	   clearBoard();
   	   output.flush();
      }
      else if(message.equals("Sending Games"))
      {
    	  getWaitingGames();
      }
      else if(message.equals("Connection Test"))
      {
    	  output.format("%s\n", "Connection Ok");
    	  output.flush();
      }
      else if(message.contains("has left the game"))
      {
    	   
    	   myTurn = false;
    	   String[] options = {"Wait for another player", "Quit the qame"};
      	   JPanel panel = new JPanel();
      	   JLabel lbl = new JLabel(message);
      	   panel.add(lbl);
      	   int choice = JOptionPane.showOptionDialog(this, panel, "Wait for another player?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options , options[0]);
      	   
      	   if(choice == 0)
      	   {
      		   
      		 output.format("%s\n","Wait for another player");
      		 clearBoard();
      	   }
      	   else
      	   {
      		 output.format("%s\n","Exit");
      		 clearBoard();
      	   }
      	   clearBoard();
      	   output.flush();
      }
      else if(message.equals("Second player connected"))
      {
    	  clearBoard();
    	  displayMessage( message + "\n" );
      }
      else
      {
         displayMessage( message + "\n" ); // display the message
      }
   } // end method processMessage
   
   private void clearBoard()
   {
	   for(int i = 1; i <= 9; i++)
	   {
		   board.get(i).setMark("");
		   board.get(i).repaint();
	   
	   }
	   boardPanel.repaint();
   }
   
   // manipulate displayArea in event-dispatch thread
   private void displayMessage( final String messageToDisplay )
   {
      SwingUtilities.invokeLater(
         new Runnable() 
         {
            public void run() 
            {
               displayArea.append( messageToDisplay ); // updates output
            } // end method run
         }  // end inner class
      ); // end call to SwingUtilities.invokeLater
   } // end method displayMessage

   // utility method to set mark on board in event-dispatch thread
   private void setMark( final Square squareToMark, final String mark )
   {
      SwingUtilities.invokeLater(
         new Runnable() 
         {
            public void run()
            {
               squareToMark.setMark( mark ); // set mark in square
            } 
         } 
      ); 
   } 

   // send message to server indicating clicked square
   public void sendClickedSquare( int location )
   {
      // if it is my turn
      if ( myTurn ) 
      {
         output.format( "%d\n", location ); // send location to server
         output.flush();
         myTurn = false; 
      } 
   } 

   // set current Square
   public void setCurrentSquare( Square square )
   {
      currentSquare = square; // set current square to argument
   } 


// private inner class for the squares on the board
   private class Square extends JPanel 
   {
      private String mark; // mark to be drawn in this square
      private int location; // location of square
   
      public Square( String squareMark, int squareLocation )
      {
         mark = squareMark; // set mark for this square
         location = squareLocation; // set location of this square

         addMouseListener( 
            new MouseAdapter() 
            {
               public void mouseReleased( MouseEvent e )
               {
                  setCurrentSquare( Square.this ); // set current square

                  // send location of this square
                  sendClickedSquare( getSquareLocation() );
               } 
            } 
         ); 
      } 

      // return preferred size of Square
      public Dimension getPreferredSize() 
      { 
         return new Dimension( 30, 30 ); // return preferred size
      } 

      
      public Dimension getMinimumSize() 
      {
         return getPreferredSize(); // return preferred size
      } 

      // set mark for Square
      public void setMark( String newMark ) 
      { 
         mark = newMark; 
         repaint(); 
      } 
   
      // return Square location
      public int getSquareLocation() 
      {
         return location; 
      } 
   
      // draw Square
      public void paintComponent( Graphics g )
      {
         super.paintComponent( g );
         g.drawRect( 0, 0, 29, 29 ); // draw square
         g.drawString( mark, 11, 20 ); // draw mark   
      } 
   }


}


