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
					sendMessage(input);
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
				//TODO Parse ack received to filter erro messages sent from the server
				ackRecived = true;
				System.out.println("ACK Recived");
				break;
			}
			catch (SocketTimeoutException e){
				System.out.println("timeout expired re-sending the message");
				ackRecived = false;
			}
		}
		socket.close();
	}
}
