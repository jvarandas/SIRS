package Bank.Client;

import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;


public class Client {
	private static final int ServerPort = 10100;
	private static final String ServerHost = "localhost";
	
	private static byte[] Confirmation_Ack = 	new byte[] {0,0};	//transfer completed
	private static byte[] Amount_Error_Ack = 	new byte[] {1,1};	//amount not available 
	private static byte[] Source_Unkown_Ack = new byte[] {1,0};		//source iban not found
	private static byte[] Dest_Unkown_Ack = 	new byte[] {0,1};	//dest iban not fount
	private static byte[] Not_Autorized_Ack = new byte[] {2,0};		//source iban doesnt belong to that port
	
	private static SecureRandom randomizer = new SecureRandom();

	public static void main(String[] args) {
		System.out.println("Client started running...");
		Scanner in = new Scanner(System.in);
		for(String input = in.nextLine(); !(input.equals("EXIT")); input = in.nextLine()){
			if(input.startsWith("SEND")){
				try {
					String writtenCommnad = input.substring(input.indexOf(" ")+1, input.length());
				//	if (checkCommand(writtenCommnad))
						sendMessage(writtenCommnad);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		in.close();
	}

	private static void sendMessage(String input) throws Exception {
		InetAddress addr = InetAddress.getByName(ServerHost);
		DatagramSocket socket = new DatagramSocket();
	
		long timestamp = new Date().getTime();
		
		
		long id =  new BigInteger(64, randomizer).longValue(); //generate a 64 bits id for the message , the probability of colision may be ignored(?)
		
		String completeMessage = input + " " + timestamp + " " + id;
				
		byte[] msgBytes = completeMessage.getBytes();

		DatagramPacket packet = new DatagramPacket(msgBytes,msgBytes.length, addr, ServerPort);
		boolean ackRecived = false;

		byte[] ack = new byte[2];
        DatagramPacket ackpacket = new DatagramPacket(ack, ack.length);

		while (!ackRecived) {
			socket.send(packet);
			System.out.println("Sent message, waiting for ack");
			
			try {
				socket.setSoTimeout(1000);
				socket.receive(ackpacket);

				ackRecived = true;
				System.out.println("ACK Received");
				
				//Process info from the ack
				byte[] ackbytes = ackpacket.getData();
				if (compareAck(ackbytes, Confirmation_Ack))
					System.out.println("Transference Completed");
				else if (compareAck(ackbytes, Amount_Error_Ack))
					System.out.println("No Funds Available");
				else if (compareAck(ackbytes, Source_Unkown_Ack))
					System.out.println("The Registered Number Does Not Have an Account");
				else if (compareAck(ackbytes, Dest_Unkown_Ack))
					System.out.println("Destination Account Not Found");
				else if (compareAck(ackbytes, Not_Autorized_Ack))
					System.out.println("You Are Not Authorized To Perform That Action");
				
				break;
			}
			catch (SocketTimeoutException e){
				System.out.println("timeout expired re-sending the message");
				ackRecived = false;
			}
		}
		socket.close();
	}
	
	private static boolean checkCommand(String input){
		String[] tokens = input.split(" ");
		if ((tokens[0].length()==25) && (tokens[1].length()==8))
			return true;
		return false;
	}
	
	
	private static void registerIBAN(String iban) throws Exception{
		sendMessage("associate "+iban);
	}
	
	private static boolean compareAck(byte[] b1, byte[] b2){
		return (b1[0]==b2[0] && b1[1]==b2[1]);
	}
}
