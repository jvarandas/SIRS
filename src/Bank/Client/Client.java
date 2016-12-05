package Bank.Client;

import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;

import Bank.Server.Message;
import Bank.Server.Exceptions.*;


public class Client {
	private static final int ServerPort = 10100;
	private static final String ServerHost = "localhost";
	
	private static InetAddress addr; 
	private static DatagramSocket socket;
	private static int port;
	private static Scanner in;
	
	public static void main(String[] args) throws UnknownHostException, SocketException {
		
		addr = InetAddress.getByName(ServerHost);
		socket = new DatagramSocket();
		System.out.println("Client started running...");
		in = new Scanner(System.in);
		for(String input = in.nextLine(); !(input.toLowerCase().equals("exit")); input = in.nextLine()){
			if(input.toLowerCase().startsWith("send ")){
				try {
					String writtenCommand = input.substring(input.indexOf(" ")+1, input.length());
					sendMessage(writtenCommand);
				} catch (Exception e) {
					System.out.println(e.getMessage());
				}
			}
			else if(input.toLowerCase().startsWith("associate ")){
				String writtenCommand = input.substring(input.indexOf(" ")+1, input.length());
				try {
					associateCommand(writtenCommand);
				} catch (Exception e) {
					e.printStackTrace();
					System.out.println(e.getMessage());
				}
			}
		}
		in.close();
	}

	private static void associateCommand(String input) throws Exception {
		Message m = new Message(input);
		
		byte[] msgBytes = m.getMessage().getBytes();
		DatagramPacket packet = new DatagramPacket(msgBytes,msgBytes.length, addr, ServerPort);
		socket.send(packet);
		
		if(!waitAck()){
			System.out.println("Operation not completed");
			return;
		}
		
		if(!confirmIdentity()){
			System.out.println("Identity not confirmed.");
			System.out.println("Operation not completed.");
			return;
		}
		port = socket.getLocalPort();
	}



	private static void sendMessage(String input) throws Exception {

		
		String info[] = input.split(" ");
	
		Message m = new Message(info[0], info[1]);
		
		byte[] msgBytes = m.getMessage().getBytes();

		DatagramPacket packet = new DatagramPacket(msgBytes,msgBytes.length, addr, ServerPort);
		
		socket.send(packet);
		
		if(!waitAck()){
			System.out.println("Operation not completed.");
			return;
		}
		
		if(!confirmIdentity()){
			System.out.println("Identity not confirmed.");
			System.out.println("Operation not completed.");
			return;
		}
		
		System.out.println("Transaction completed with success");
	}
	
	private static boolean waitAck() throws IOException {

		boolean ackReceived = false;
		boolean timeout = false;

		byte[] ack = new byte[120];
        DatagramPacket ackpacket = new DatagramPacket(ack, ack.length);

		while (!ackReceived && !timeout) {
			System.out.println("Sent message, waiting for ack");
			
			try {
				socket.setSoTimeout(1000);
				socket.receive(ackpacket);
				//socket.setSoTimeout(0);

				String content[] = new String(ackpacket.getData()).split("\\|\\|");
				
				if(content[0].equals("ack")){
					System.out.println("Acknowlegde received");
					ackReceived = true;
					String client_info = "";
					if(content[2].equals("confirmed")){
						//socket.close();
						return true;
					}else if(content[2].equals("amount_error")){
						client_info = "The amount entered is higher than the current balance.";
					}else if(content[2].equals("destination_unknown")){
						client_info = "The IBAN entered is not registered.";
					}else if(content[2].equals("source_unknown")){
						client_info = "The IBAN entered is not registered.(Source)";
					}else if(content[2].equals("not_authorized")){
						client_info = "Operation not authorized";
					}
					System.out.println(client_info);
				}
			}
			catch (SocketTimeoutException e){
				System.out.println("timeout expired");
				ackReceived = false;
				timeout = true;
			}
		}
		
		//socket.close();
		return false;
	}
	
	private static boolean confirmIdentity() throws IOException {
		
		byte[] codes = new byte[120];
		DatagramPacket codePacket = new DatagramPacket(codes, codes.length);
		
		try {
			socket.setSoTimeout(10000);
			socket.receive(codePacket);
			//socket.setSoTimeout(0);
			
				
		} catch (SocketTimeoutException e) {
			System.out.println("Server did not send confirmation in time.");
			System.out.println("Operation canceled");
			return false;
		}
		String content[] = new String(codePacket.getData()).split("\\|\\|");
		
		if(!content[0].equals("codes")){
			return false;
		}
		
		System.out.println(content[2]);
		Scanner in2 = new Scanner(System.in);
		String input = in2.nextLine();
		char[] code = input.toCharArray();
		Message m = new Message(code);
		in = in2;
		byte[] answer = m.getMessage().getBytes();
		DatagramPacket answerPacket = new DatagramPacket(answer, answer.length, addr, ServerPort);
		socket.send(answerPacket);
				
		if(!waitAck()){
			System.out.println("Operation not completed.");
			return false;
		}
		
		return true;
	}
	
}
