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
	private static int phone_number;
	
	private static SecureRandom randomizer = new SecureRandom();
	
	private static BigInteger b = new BigInteger(10, randomizer);
	private static BigInteger sessionKey;
	
	public static void main(String[] args) throws Exception {
		
		addr = InetAddress.getByName(ServerHost);
		socket = new DatagramSocket();
		
		requestPort();
		System.out.println("Client started running...");
		
		generateDHPublicValues();
		generateDHSecretKey();
		
		in = new Scanner(System.in);
		
		setPhoneNumber();
		System.out.println("Client started running...");
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
			else {
				System.out.println("Comando desconhecido");
				System.out.println("Comandos disponiveis: SEND\tASSOCIATE");
			}
		}
		in.close();
	}
	
	private static void requestPort() throws IOException {
		Message m = new Message();
		byte[] msgBytes = m.getMessage().getBytes();
		DatagramPacket packet = new DatagramPacket(msgBytes,msgBytes.length, addr, ServerPort);
		
		socket.send(packet);
		
		byte[] ack = new byte[240];
        DatagramPacket ackpacket = new DatagramPacket(ack, ack.length);
        
        socket.receive(ackpacket);
		
        String content[] = new String(ackpacket.getData()).split("\\|\\|");
        
        if(content[0].equals("port")){
        	port = Integer.parseInt(content[2]);
        	System.out.println("Joined at port " + port);
        }
        else System.exit(-1);
		
	}

	
	public static void setPhoneNumber(){

		String input = new String();
		
		System.out.println("Insert the phone number that you pretend associate to your account");
		
		while(true){
			input = in.nextLine();
			
			if(input.matches("[0-9]+") && input.length() == 9){
				phone_number = Integer.parseInt(input);
				break;
			}else{
				System.out.println("The number is invalid, it should have 9 digits");
			}
		}
	}
	
	
	
	private static void generateDHPublicValues() throws Exception{
		
		
		BigInteger p = new BigInteger(10, randomizer).abs();
		BigInteger q = new BigInteger(10, randomizer).abs();
		BigInteger yB = new BigInteger(10, randomizer).abs();
		
		int bitLength = 512; // 1024 bits
	    
	    p = BigInteger.probablePrime(bitLength, randomizer).abs();
	    q = BigInteger.probablePrime(bitLength, randomizer).abs();
	    b = BigInteger.probablePrime(bitLength, randomizer).abs();
	    yB = p.modPow(b, q);
	    
	    System.out.println("P= "+p);
	    Message m = new Message(p);
	    byte[] keys = m.getMessage().getBytes();
	    DatagramPacket keysPacket = new DatagramPacket(keys, keys.length, addr, port);
	    socket.send(keysPacket);
	    m = new Message(q);
	    keys = m.getMessage().getBytes();
	    keysPacket = new DatagramPacket(keys, keys.length, addr, port);
	    socket.send(keysPacket);
	    m = new Message(yB);
	    keys = m.getMessage().getBytes();
	    keysPacket = new DatagramPacket(keys, keys.length, addr, port);
	    socket.send(keysPacket); 
	    
	}
		
	private static void generateDHSecretKey() throws IOException{
		byte[] keys = new byte[240];
		DatagramPacket keysPacket = new DatagramPacket(keys, keys.length);
		socket.receive(keysPacket);
		
		String message = new String(keysPacket.getData());
		String[] content = message.split("\\|\\|");
		BigInteger q = new BigInteger(content[2]);
		
		
		keys = new byte[240];
		keysPacket = new DatagramPacket(keys, keys.length);
		socket.receive(keysPacket);
		
		message = new String(keysPacket.getData());
		content = message.split("\\|\\|");
		BigInteger yA = new BigInteger(content[2]);
		
		sessionKey = yA.modPow(b, q);
		System.out.println(sessionKey+"");
		
	}
	
	private static void associateCommand(String input) throws Exception {
		Message m = new Message(input, phone_number);
		
		byte[] msgBytes = m.getMessage().getBytes();
		DatagramPacket packet = new DatagramPacket(msgBytes,msgBytes.length, addr, port);
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
	}



	private static void sendMessage(String input) throws Exception {

		
		String info[] = input.split(" ");
	
		Message m = new Message(info[0], info[1], phone_number);
		
		byte[] msgBytes = m.getMessage().getBytes();

		DatagramPacket packet = new DatagramPacket(msgBytes,msgBytes.length, addr, port);
		
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

		byte[] ack = new byte[240];
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
		
		byte[] codes = new byte[240];
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
		DatagramPacket answerPacket = new DatagramPacket(answer, answer.length, addr, port);
		socket.send(answerPacket);
				
		if(!waitAck()){
			System.out.println("Operation not completed.");
			return false;
		}
		
		return true;
	}
	
}
