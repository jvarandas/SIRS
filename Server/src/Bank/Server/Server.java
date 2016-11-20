package Bank.Server;

import java.io.IOException;
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
			System.out.println("Recived: "+ out);
			//Thread.sleep(1000);
			byte[] ackToSend = parseMessage(out, packet.getSocketAddress());
			sendAck(packet.getAddress(), packet.getPort(), ackToSend);
		
		}
	}
	
	public static byte[] parseMessage(String msg, SocketAddress sender){
		if (msg.split(" ")[0].compareTo("associate")==0){  //TO register the "phone number" associated with an account 
			String iban = msg.split(" ")[1].trim();			//Since client ports are not fixed we need to register them at the beginning of each run
			MatrixCard d = new MatrixCard(iban);
			ClientsMatrix.put(iban, d.getContent());
			Contacts.put(sender, iban);
			byte[] ackPacket = new byte[2];
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
	
	private static byte[] processTransfer(String source, String dest, int ammount){ //TODO throw excetions 
	    
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
		}
		return ackPacket;
	}
	
	public static void sendAck(InetAddress address, int port, byte[] ackPacket) throws IOException{
	    DatagramPacket acknowledgement = new  DatagramPacket(ackPacket, ackPacket.length, address, port);
	    socket.send(acknowledgement);
	    System.out.println("Sent ack");
}

}
