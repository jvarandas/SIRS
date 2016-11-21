package Bank.Client;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Scanner;


public class Client {
	private static final int ServerPort = 10100;
	private static final String ServerHost = "localhost";

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
		
		byte[] msgbytes = new byte[120];
		msgbytes = input.getBytes();
		
		DatagramPacket packet = new DatagramPacket(msgbytes,msgbytes.length, addr, ServerPort);
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
				if (ackbytes[0]==0 && ackbytes[1]==0)
					System.out.println("Transaction is completed");
				else if (ackbytes[0]==1)
					System.out.println("Message error: Invalid Destination IBAN");
				else if (ackbytes[1]==1)
					System.out.println("Message Error: Unavailable Ammount for transfer");
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
}
