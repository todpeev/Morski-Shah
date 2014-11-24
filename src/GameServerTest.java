import javax.swing.JFrame;

public class GameServerTest
{
   public static void main( String[] args )
   {
      Server application = new Server();
      application.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
      application.execute();
   } 
}