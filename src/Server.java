import java.awt.BorderLayout;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

@SuppressWarnings("serial")
public class Server extends JFrame 
{

   private JTextArea outputArea; // for outputting moves
   private ServerSocket server; // server socket to connect with clients
   private ExecutorService playerThreads; // will run players
   private final int maxPlayers = 1000;
   private HashMap<String, Game> games;
   private static CopyOnWriteArrayList<Player> players;//list of playyers and their connections
     
   // set up tic-tac-toe server and GUI
   public Server()
   {
      super( "Server" );
      games = new HashMap<String, Game>();
      players = new CopyOnWriteArrayList<>();
      
      // create ExecutorService with a thread for each player
      playerThreads = Executors.newFixedThreadPool( maxPlayers );
      try
      {
    	  // set up ServerSockets
         server = new ServerSocket( 12345, maxPlayers ); 
      } 
      catch ( IOException ioException ) 
      {
         ioException.printStackTrace();
         System.exit( 1 );
      } 

      outputArea = new JTextArea(); // create JTextArea for output
      add( outputArea, BorderLayout.CENTER );
      outputArea.setText( "Server awaiting connections\n" );
      setSize( 300, 300 ); // set size of window
      setVisible( true ); // show window
   } 

   // wait for two connections so game can be played
   public void execute()
   {

	   // wait for each client to connect
      while ( true ) 
      {
    	  if(players.size()<=maxPlayers)
    	  {
    		  try // wait for connection, create Player, start runnable
    		  {
    			  Socket socket = server.accept();
    			  Player newPlayer = new Player( socket);
    			  players.add(newPlayer);
    			  playerThreads.execute( newPlayer); // execute player runnable
    		  } 
    		  catch ( IOException ioException ) 
    		  {
    			  ioException.printStackTrace();
    			  System.exit( 1 );
    		  } 
    	  }
    	  else 
    	  {
    		  displayMessage("Max number of players reached. New connections will be refused");
    	  }
         
      
      } 
   } 
   
   // display message in outputArea
   private void displayMessage( final String messageToDisplay )
   {
      // display message from event-dispatch thread of execution
      SwingUtilities.invokeLater(
         new Runnable() 
         {
            public void run() // updates outputArea
            {
               outputArea.append( messageToDisplay ); 
            } 
         } 
      ); 
   } 

      
   // private class Player makes each Player separate thread
   private class Player implements Runnable 
   {
      private Socket connection; // connection to client
      private Scanner input; // input from client
      private Formatter output; // output to client
      private int playerNumber; // tracks which player this is
      private String mark; // mark for this player
      private String message;
      private int[] rowScore;
      private int[] columnScore;
      private int[] crossScore;
      private Game game;
      private String playerName;
      private boolean playGame;
      private boolean isOnline;
      private boolean waitingForResponce = false;
          
      // set up Player thread
      public Player( Socket socket )
      {
   	  
    	 isOnline = true;
    	 playGame = true;
         rowScore = new int[3];
         columnScore = new int[3];
         crossScore = new int[3];
    	 connection = socket; // store socket for client
    	 try // obtain streams from Socket
         {
            input = new Scanner( connection.getInputStream() );
            output = new Formatter( connection.getOutputStream() );
         } 
         catch ( IOException ioException ) 
         {
            ioException.printStackTrace();
            System.exit( 1 );
         } 
      } 

      // send message that other player moved
      public void otherPlayerMoved( int location )
      {
         output.format( "Opponent moved\n" );
         output.format( "%d\n", location ); // send location of move
         output.flush(); 
      } 

      // control thread's execution
      public void run()
      {
         int location = 0;
        try 
        {
            playerName =input.nextLine();
            displayMessage( "Player "+playerName+" connected\n" );
            output.format("You sucessfully connected to the game server\n" );
            output.flush(); // flush output

            while(isOnline){
         
            	sendWaitingGames();
      
            	
            		message = (String) input.nextLine();
            		if(message.equals("Create New Game"))
            		{
            			playerNumber = 0;
            			game = new Game(input.nextLine(), this);
            			games.put(game.gameName,game);
            			game.marksTaken[0] = true;
            			mark = game.MARKS[0];
            		}
            		else
            		{
            		
            			
            			while(true)
            			{
            				if(games.containsKey(message))
            				{
            					game = games.get(message);
            					game.joinGame(this);
            					games.remove(message);
            					for(int j = 0; j < 2; j++)
            					{
            						if(game.marksTaken[j] == false)
            						{
            							mark = game.MARKS[j];
            							playerNumber = j;
            							game.marksTaken[j] = true;
            							break;
            						}
            					}
            					
            					break;
            				} else 
            				{
            					output.format("Invalid game.Choose agian\n");
            					output.flush();
            					message = input.nextLine();
            				}
            			}
            		}
            output.format("%s\n",mark);
            output.flush();	            	            	
            	
            while(playGame)// play sucessive string of games
            {
            	if(game.startComputer)
            	{
            		System.out.println("Waiting for computer");
            		Thread.sleep(500000000);
            	} 
            	else
            	{
            		waitForSecondPlayer();
            		if(this.playerNumber == game.currentPlayer)
            		{
            			output.format("%s\n", "Your turn");
            			output.flush();
            		}
            		while (!game.isRoundOver()) // while current game game not over
            		{
            		     // while not current player, must wait for turn
            		     while ( this.playerNumber != game.currentPlayer ) 
            		     {
            		         game.gameLock.lock(); // lock game to wait for other player to go

            		         try 
            		         {
            		            game.otherPlayerTurn.await(5000, TimeUnit.MILLISECONDS); // wait for player's turn
            		            testConnection();
            		           
            		         } 
            		         catch ( InterruptedException exception )
            		         {
            		            throw new Exception();
            		         } 
            		         finally
            		         {
            		            game.gameLock.unlock(); // unlock game after waiting
            		            if(game.isRoundOver())
            		            {
            		            	break;
            		            }
            		         } 
            		     } 
            			
            		    if(game.isRoundOver())
            			{
            				break;
            			}
            		    String a = null;
            		    location = 0; //deafult location- not valid;
            			while(true)
            			{
            				try
            				{
            					a = input.nextLine();
            					if(a.equals("End Game"))
            					{
            						
            					} 
            					else 
            					{
            						location = Integer.parseInt(a);
            					}
            					
            					break;
            				}
            				catch (NumberFormatException e){
            					continue;
            				}
            			}
            				
            			if(location == 0)
            			{
            				break;
            			}
            			// check for valid move
            			if ( game.validateAndMove( location, playerNumber ) ) 
            			{
            				if(game.players[game.currentPlayer] != null)
            		        {            		         
            					displayMessage( "location: " + location +"\n" );
            					output.format( "Valid move.\n" ); // notify client
            					output.flush(); // flush output            		        
            		        }
            			} 
            			else 
            			{
            				output.format( "Invalid move, try again\n" );
            				output.flush();
            			}	 
            		}            		
            	
            		if(game.getWinner() !=null)
            		{
            			if(game.winner == this){
            				output.format("%s\n", "You win the game!");
            				output.flush();
            			} else {
            				output.format("%s\n", game.getWinner().playerName +" wins !");
           					output.flush();
            			}
            			
            		}

            
            		waitingForResponce = true;
            		if(location !=0)
            		{
            			input.nextLine();
            		}
            		
            		if(input.hasNext())
            		{            			
            			String message = input.nextLine();
            			System.out.println(message);
            			if(message.equals("Stop Playing"))
            			{            				
            				clearScore();
            				playGame = false;
            				int playersLeft = 2;
            				for(int i = 0;i < 2; i++)
            				{
            					if(game.players[i] == null)
            					{
            						playersLeft--;
            					}
            					if(game.MARKS[i].equals(mark))
            					{
            						game.marksTaken[i] = false;
            					}
            					if(game.players[i] == this)
            					{
            						game.players[i] = null;
            						playersLeft--;
            					}
            				}
            				
            				if(playersLeft == 1)
            				{
            					games.put(game.gameName, game);
            					
            				} 
            				else if(playersLeft == 0)
            				{
            					games.remove(game.gameName);
            					game = null;
            				}
            			} else
                		{
                			clearScore();
                			waitingForResponce = false;
                			output.format("%s\n", "New Round, waiting for the other player to respond");
                			output.flush();
                		}
            			
            		} 
            		waitingForResponce = false;
            		for ( int i = 0; i <= 9; i++ )
         			   game.board[ i ] = new String( "" );
            		
            		
            	}
                }
             }
          } 
          catch(Exception e)
          {
        	  //e.printStackTrace();
        	  displayMessage("Player " + playerName + " disconnected\n");
  			
        	  
        	  if(playGame == true){
        		  for(int i = 0 ; i < 2; i++)
        		  {
        			  if(game.players[i] != this && game.players[i] != null)
        			  {
        				  game.players[i].output.format("%s\n", "Your opponent has left");
        				  game.players[i].output.flush();
        			  }
        		  } 
        	  }
        	 
        	  
        	  
        	  try 
        	  {
        		  removeOfflinePlayers();
			  } catch (Exception e1) 
        	  {
        		  e1.printStackTrace();
        	  }
                     	  
          }
       
      } 

      
      public void waitForSecondPlayer() throws Exception
      {
    	
    	boolean wait;  
    	game.numberOfPlayers++;    	
    	if(game.numberOfPlayers == 1)
    	{
    		wait = true;
    	} else {
    		
    		wait =false;
    	}
    	
    	if ( wait ) 
      	{
         
      	
      		output.format( "%s\n%s\n", "New Game Initialized",
      				"Waiting for another player\n" );
      		output.flush(); // flush output
      		game.gameLock.lock(); // lock game to  wait for second player

      		try 
      		{
      			while( game.suspended )
      			{
      				game.otherPlayerConnected.await(5000, TimeUnit.MILLISECONDS); // wait for player O
      				testConnection();
		           
      			} 
      		} 
      		catch ( InterruptedException exception ) 
      		{
      			exception.printStackTrace();
      		} 
      		finally
      		{
      			game.gameLock.unlock(); // unlock game after second player
      		} 

      		// send message that other player connected
      		output.format( "Second player connected.\n" );
      		output.flush(); 
      		game.setSuspended(true);
      	} 
      	else
      	{
      		output.format( "Player O entered the game, please wait for Player 1 to move\n" );
      		output.flush(); // flush output
      		game.gameLock.lock(); // lock game to signal player X's thread
      		game.numberOfPlayers = 0;
      		game.isInProgress = true;
      		game.winner = null;
      		try
      		{
      			game.setSuspended( false ); // resume player X
      			game.otherPlayerConnected.signal(); // wake up player X's thread
      		} 
      		finally
      		{
      			game.gameLock.unlock(); // unlock game after signalling player X
      		} 
      	} 
      }
      
      public void testConnection() throws Exception
      {    	  
    	  output.format("%s\n", "Connection Test");
      	  output.flush();    	  
    	  String answer = input.nextLine();
      	  System.out.println(answer+ " "+ playerName);
      }
      
       public void removeOfflinePlayers() throws Exception
      {
    	  connection.close();
    	  int offlineCounter = 0;
			
    	  for(int i = 0;i < 2;i++)
		  {
				if(game.players[i] == this)
				{
					game.players[i] = null;
					game.marksTaken[i] = false;
					
				}
				if(game.players[i] == null)
				{
					offlineCounter++;
				}
		  }
    	  if(offlineCounter == 2)
			{
				games.remove(game.gameName);
				game = null;
			}
			else if(offlineCounter == 1)
			{
				games.put(game.gameName, game);
			}
    	  players.remove(this);
    	  
      }
      
      public void clearScore()
      {
    	  for ( int i = 0; i <= 9; i++ )
     	 {
     		 game.board[ i ] = new String( "" );
     	 }
    	  for(int i = 0; i < 3 ; i++)
    	  {
    		  rowScore[i] = 0;
    		  columnScore[i] = 0;
    		  crossScore[i] = 0;
    	  }
      }
     
      public void sendWaitingGames()
      {
    	  output.format("%s\n", "Sending Games");
    	  output.flush();
    	  for (Map.Entry<String, Game> entry : games.entrySet()) 
    	  {
    		  	output.format(entry.getKey()+"\n");
    			output.flush();
    	  }
    	  output.format("%s\n", "Finished Sending Games");
    	  output.flush();
      }
   } 
      
  private class Game
  {
	   private Lock gameLock; // to lock game for synchronization
	   private Condition otherPlayerConnected; // to wait for other player
	   private Condition otherPlayerTurn; // to wait for other player's turn
	   private String[] board = new String[ 10 ]; // tic-tac-toe board
	   private int currentPlayer; // keeps track of player with current move
	   private final int PLAYER_X = 0; // constant for first player
	   private final int PLAYER_O = 1; // constant for second player
	   private final String[] MARKS = { "X", "O" }; // array of marks
	   private Player[] players; // array of Players
	   private String gameName; 
	   private Player winner;
	   private boolean suspended = true; // whether game is suspended
	   private boolean[] marksTaken = {false,false};
	   private int numberOfPlayers = 0;
	   private boolean isInProgress = false;
	   private boolean startComputer = false;
	   
	   
	   public Game(String name, Player firstPlayer)
	   {	
		   players = new Player[2];
		   gameLock = new ReentrantLock(); // create lock for game
		   players[0] = firstPlayer;
		   players[1] = null;
		   gameName = name;
	      // condition variable for both players being connected
	      otherPlayerConnected = gameLock.newCondition();
	      // condition variable for the other player's turn
	      otherPlayerTurn = gameLock.newCondition();      
	      for ( int i = 0; i < board.length; i++ )
	      board[ i ] = new String( "" ); // create tic-tac-toe board
	      setPlayOrder();
   	   }
	   
	   // set whether or not game is suspended
	   public void setSuspended( boolean status )
	   {
	         suspended = status; 
	   }
	   
	   public void joinGame(Player player){
		   for(int i = 0; i < 2;i++)
		   {
			   if(players[i] == null)
			   {
				   players[i] = player;
				   break;
			   }
		   }
		   
	   }
	   
	   public Player getWinner()
	   {
		   return winner;
	   }
	// determine if move is valid
	   public boolean validateAndMove( int location, int player )
	   {
	      // if location not occupied, make move
	      if ( !isOccupied( location ) )
	      {
	         board[ location ] = MARKS[ currentPlayer ]; // set move on board
	         int pos = location -1;
	         players[currentPlayer].rowScore[(pos/3)] = players[currentPlayer].rowScore[(pos/3)] + 1;
	         players[currentPlayer].columnScore[(pos%3)] = players[currentPlayer].columnScore[(pos%3)] + 1;
	         // diagonal:
	         if(((pos/3) == pos % 3) ||
	        		 (( pos % 3) == 0 && (pos/3) == 2 ) ||
	        		 ((pos % 3) == 2 && (pos/3) == 0 )
	        	)
	         {
	        	 if((pos/3) == (pos % 3))
	        	 {
	        		 //update the two cross scores:
	        		 players[currentPlayer].crossScore[0] = players[currentPlayer].crossScore[0] + 1;
	        		 if(pos/3 == 1)
	        		 {
	        			 players[currentPlayer].crossScore[1] = players[currentPlayer].crossScore[1] + 1;
	        		 }
	        	 } 
	        	 else 
	        	 {
	        		 players[currentPlayer].crossScore[1] = players[currentPlayer].crossScore[1] + 1;
	        		 
	        	 }
	         }
	         currentPlayer = ( currentPlayer + 1 ) % 2; // change player
	         if(players[currentPlayer] == null)
	         {	        	       	 
	        	 try {
					players[( currentPlayer + 1 ) % 2].testConnection();
				} catch (Exception e) {
					e.printStackTrace();
				}
	        	return true;
	         }
	         // let new current player know that move occurred
	         players[ currentPlayer ].otherPlayerMoved( location );
	         gameLock.lock(); // lock game to signal other player to go

	         try 
	         {
	            otherPlayerTurn.signal(); // signal other player to continue
	         } // end try
	         finally
	         {
	            gameLock.unlock(); // unlock game after signaling
	         } 

	         return true; 
	      } 
	      else // move was not valid
	         return false; 
	   } 

	   // determine whether location is occupied
	   public boolean isOccupied( int location )
	   {
	      if ( board[ location ].equals( MARKS[ PLAYER_X ] ) || 
	         board [ location ].equals( MARKS[ PLAYER_O ] ) )
	         return true; // location is occupied
	      else
	         return false; // location is not occupied
	   } 

	   
	   public boolean isRoundOver()
	   {
		 		  
		  for(int i = 0; i < 3; i++)
		  {
			  for(int j = 0; j < 2; j++)
			  {
				if(players[j] == null){
					isInProgress = false;
					return true;
				}
					
					
				
				if(players[j].rowScore[i] == 3 || players[j].columnScore[i] == 3 || players[j].crossScore[i] == 3)
				{
					winner = players[j];
					isInProgress = false;
					setPlayOrder();
					return true;
				}
			  }
		  }
		  
	      return false; 
	   } 
	   
	   private void setPlayOrder()
	   {		   				   
		   Random rand = new Random();
		   int  n = rand.nextInt(2);
		   currentPlayer = n;		 	
	   }
	   
  }
} 



