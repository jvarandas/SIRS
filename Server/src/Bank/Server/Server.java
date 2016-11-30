package Bank.Server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;


public class Server {
	private static File _config;
	private static DatagramSocket socket;
	private static HashMap<String, Integer> Bank = new HashMap<String, Integer>(); //Emulate the bank
	private static HashMap<SocketAddress, String> Contacts = new HashMap<SocketAddress, String>(); //Associated addrs for each account
	private static HashMap<String,List<String>> ClientsMatrix = new HashMap<String,List<String>>();
	private static HashMap<String, String> Passwords = new HashMap<String, String>();
	private static List<Long> ID_Bucket = new ArrayList<Long>();  //to save the received IDS
	
	private static byte[] Confirmation_Ack = 	new byte[] {0,0};	//transfer completed
	private static byte[] Amount_Error_Ack = 	new byte[] {1,1};	//amount not available 
	private static byte[] Source_Unkown_Ack = new byte[] {1,0};		//source iban not found
	private static byte[] Dest_Unkown_Ack = 	new byte[] {0,1};	//dest iban not fount
	private static byte[] Not_Autorized_Ack = new byte[] {2,0};		//source iban doesnt belong to that port
	
	private static long Max_Time_Diff = 2000; //max difference of 2 seconds
	
	public static void main(String[] args) throws Exception {
		System.out.println("Server started running");
		_config = new File("bank.cnf");
	
		byte[] buffer = new byte[120];
		socket = new DatagramSocket(10100);
		
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		byte[] ackToSend;
		
		System.out.println("Running - Awaiting Messages");
		System.out.println("Press enter to shutdown");
		
		while(true){
			ackToSend = Not_Autorized_Ack; //by default
			socket.receive(packet);
			String message = new String(packet.getData());
			System.out.println("Received: "+ message);
			//Thread.sleep(1000);
			if (validateTimestamp(message) && validateID(message)){
				ackToSend = parseMessage(message, packet.getSocketAddress());
			}
			sendAck(packet.getAddress(), packet.getPort(), ackToSend);
		
		}
	}
	
	private static boolean validateTimestamp(String msg){
		long timestamp = Long.parseLong(msg.split(" ")[2].trim());
		long current_time = new Date().getTime();
		
		if (current_time-timestamp > Max_Time_Diff)
			return false;
		else 
			return true;
	}
	
	private static boolean validateID(String msg){
		long id = Long.parseLong(msg.split(" ")[3].trim());
		 if (!ID_Bucket.contains(id)){
			 ID_Bucket.add(id);
			 return true;
		 }
		 else return false;
	}
	
	private static byte[] parseMessage(String msg, SocketAddress sender) throws IOException{
		if (msg.split(" ")[0].compareTo("associate")==0){  //TO register the "phone number" associated with an account 
			String iban = msg.split(" ")[1].trim();			//Since client ports are not fixed we need to register them at the beginning of each run
			MatrixCard d = new MatrixCard(iban);
			String password = msg.split(" ")[2];
			
			if(existsIn(iban)){
				System.out.println("That iban exists associated with another account");
				return Not_Autorized_Ack;
			}
			
			Passwords.put(iban, password);
			Bank.put(iban, 1000); //INICIALIZA UMA CONTA COM 1000€ POR DEFAULT
			ClientsMatrix.put(iban, d.getContent());
			Contacts.put(sender, iban);
			
			confFile(Passwords, Bank, Contacts);
			
			return  Confirmation_Ack;
		}
				
			
		String[] info = msg.split(" ");
		String destAccount = info[0];
		
		int amount = Integer.parseInt(info[1].trim());
		//get IBAN from sender addr
		String sourceAccount = Contacts.get(sender);
		return processTransfer(sourceAccount, destAccount, amount);

	}
	
	private static void confFile(HashMap<String, String> passwords, HashMap<String, Integer> bank, HashMap<SocketAddress, String> contacts) throws IOException{
		
		BufferedWriter output = null;
		String titulo = new String();
		String iban_aux = new String();
		String password_aux = new String();
		String text = new String();
		int saldo;
		
        try {
            FileOutputStream fos = new FileOutputStream(_config);
            
            output = new BufferedWriter(new OutputStreamWriter(fos));
            
            titulo = "Addres\t\t\tIban\tSaldo\tPassword";
            
            for(SocketAddress address: contacts.keySet()){
            	iban_aux = contacts.get(address);
            	saldo = bank.get(iban_aux);
            	password_aux = passwords.get(iban_aux);
            	
            	text += address+"\t"+iban_aux+"\t"+saldo+"\t"+password_aux+"\n";
            }
            
            output.write(titulo);
            output.newLine();
            output.write(text);
            output.close();
            
        } catch ( IOException e ) {
            e.printStackTrace();
            
        }
	}
	
	private static boolean existsIn(String iban) throws IOException{
		
		try {
			BufferedReader br = new BufferedReader(new FileReader(_config.getName()));
		    String line = br.readLine();
		    String[] aux;

		    while (line != null) {
		        
		    	aux = line.split(" ");
		    	
			    for(String s: aux)	
		    		if(s.equals(iban))
			    		return true;
		    	
		        line = br.readLine();
		    }
		    
		    br.close();
		    
		} catch(FileNotFoundException f){
			return false;
			
		}
		
		return false;
	}
	
	private static byte[] processTransfer(String source, String dest, int ammount) throws IOException{
	    
		if (!Bank.containsKey(dest))
			return Dest_Unkown_Ack;
		
		else if (!Bank.containsKey(source))
			return Source_Unkown_Ack;
		
		else if (Bank.get(source)<ammount)
			return Amount_Error_Ack;
		
		else {
			Bank.put(source, Bank.get(source)-ammount);
			Bank.put(dest, Bank.get(dest)+ammount);
		    confFile(Passwords, Bank, Contacts);
		    return Confirmation_Ack;
		}
	}
	
	private static void sendAck(InetAddress address, int port, byte[] ackPacket) throws IOException{
	    DatagramPacket acknowledgement = new  DatagramPacket(ackPacket, ackPacket.length, address, port);
	    socket.send(acknowledgement);
	    System.out.println("Sent ack");
}

}
