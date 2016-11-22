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
import java.util.HashMap;
import java.util.List;


public class Server {
	private static DatagramSocket socket;
	private static HashMap<String, Integer> Bank = new HashMap<String, Integer>(); //Emulate the bank
	private static HashMap<SocketAddress, String> Contacts = new HashMap<SocketAddress, String>(); //Associated addrs for each account
	private static HashMap<String,List<String>> ClientsMatrix = new HashMap<String,List<String>>();
	private static HashMap<String, String> Passwords = new HashMap<String, String>();
	
	public static void main(String[] args) throws Exception {
		System.out.println("Server started running");
		byte[] buffer = new byte[120];
		socket = new DatagramSocket(10100);
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		
		System.out.println("Running - Awaiting Messages");
		System.out.println("Press enter to shutdown");
		
		while(true){
			socket.receive(packet);
			String out = new String(packet.getData());
			System.out.println("Received: "+ out);
			//Thread.sleep(1000);
			byte[] ackToSend = parseMessage(out, packet.getSocketAddress());
			sendAck(packet.getAddress(), packet.getPort(), ackToSend);
		
		}
	}
	
	private static byte[] parseMessage(String msg, SocketAddress sender) throws IOException{
		if (msg.split(" ")[0].compareTo("associate")==0){  //TO register the "phone number" associated with an account 
			String iban = msg.split(" ")[1].trim();			//Since client ports are not fixed we need to register them at the beginning of each run
			MatrixCard d = new MatrixCard(iban);
			String password = msg.split(" ")[2];
			byte[] ackPacket = new byte[2];
			
			if(existsIn(iban)){
				System.out.println("That iban exists associated with another account");
				ackPacket[0] = 1;
				ackPacket[1] = 1;
				return ackPacket;
			}
			
			Passwords.put(iban, password);
			Bank.put(iban, 1000); //INICIALIZA UMA CONTA COM 1000€ POR DEFAULT
			ClientsMatrix.put(iban, d.getContent());
			Contacts.put(sender, iban);
			
			confFile(Passwords, Bank, Contacts);
			
			ackPacket[0] = (byte)(0);
		    ackPacket[1] = (byte)(0);
			return  ackPacket;
		}
				
			
		String[] info = msg.split(" ");
		String destAccount = info[0];
		
		int amount = Integer.parseInt(info[1].trim());
		//get IBAN from sender addr
		String sourceAccount = Contacts.get(sender);
		return processTransfer(sourceAccount, destAccount, amount); //TODO try and catch

	}
	
	private static void confFile(HashMap<String, String> passwords, HashMap<String, Integer> bank, HashMap<SocketAddress, String> contacts) throws IOException{
		
		BufferedWriter output = null;
		String titulo = new String();
		String iban_aux = new String();
		String password_aux = new String();
		String text = new String();
		int saldo;
		
        try {
            File file = new File("bank.cnf");
            FileOutputStream fos = new FileOutputStream(file);
            
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
			BufferedReader br = new BufferedReader(new FileReader("bank.cnf"));
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
			File file = new File("bank.cnf");
			return false;
			
		}
		
		return false;
	}
	
	private static byte[] processTransfer(String source, String dest, int ammount) throws IOException{ //TODO throw excetions 
	    
		byte[] ackPacket = new byte[2];
	    
		if (!Bank.containsKey(dest)){
			ackPacket[0] = (byte)(1);
		}
		else if (Bank.get(source)<ammount){
			ackPacket[1]= (byte)(1);
		}
		else {
			Bank.put(source, Bank.get(source)-ammount);
			Bank.put(dest, Bank.get(dest)+ammount);
			ackPacket[0] = (byte)(0);
		    ackPacket[1] = (byte)(0);
		    confFile(Passwords, Bank, Contacts);
		}
		return ackPacket;
	}
	
	private static void sendAck(InetAddress address, int port, byte[] ackPacket) throws IOException{
	    DatagramPacket acknowledgement = new  DatagramPacket(ackPacket, ackPacket.length, address, port);
	    socket.send(acknowledgement);
	    System.out.println("Sent ack");
}

}
