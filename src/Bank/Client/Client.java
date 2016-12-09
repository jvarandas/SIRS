package Bank.Client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import Bank.Server.AES;
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
	private static String sessionKey = "1234567891234567";
	
	private static AES cbc;
	
	public static void main(String[] args) throws Exception {
		cbc = new AES(sessionKey);
		addr = InetAddress.getByName(ServerHost);
		socket = new DatagramSocket();
		
		requestPort();
		System.out.println("Client started running...");
		
		//generateDHPublicValues();
		//generateDHSecretKey();
		
		in = new Scanner(System.in);
		
		setPhoneNumber();
		System.out.println("Client started running...");
		for(String input = in.nextLine(); !(input.toLowerCase().equals("exit")); input = in.nextLine()){
			if(input.toLowerCase().startsWith("send ")){
				try {
					String writtenCommand = input.substring(input.indexOf(" ")+1, input.length());
					sendEncryptedMessage(writtenCommand);
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
	
	private static void requestPort() throws IOException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, InvalidAlgorithmParameterException {
		Message m = new Message();
		m.setKey(sessionKey);
		byte[] msgBytes = m.getMessageBytes();
		DatagramPacket packet = new DatagramPacket(msgBytes,msgBytes.length, addr, ServerPort);
		
		socket.send(packet);
		
		byte[] ack = new byte[120];
        DatagramPacket ackpacket = new DatagramPacket(ack, ack.length);
        
        socket.receive(ackpacket);
		
        String content[] = new String(ackpacket.getData()).split("\\|\\|");
        System.out.println(content[2]);
        String nr_port = content[2].substring(0, 5);
        if(content[0].equals("port")){
        	port = Integer.parseInt(nr_port);
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
		
		int bitLength = 1024; // 1024 bits
	    
	    p = BigInteger.probablePrime(bitLength, randomizer);
	    System.out.println("p: "+p);
	    
	    q = BigInteger.probablePrime(bitLength, randomizer);
	    System.out.println("q: "+q);
	    
	    b = BigInteger.probablePrime(bitLength, randomizer);
	    
	    yB = p.modPow(b, q);
	    System.out.println("yB: "+yB);
	    System.out.println("yB size COUNT= "+yB.bitCount());
		System.out.println("yB size LENGTH= "+ yB.bitLength());
	    
	    List<byte[]> messages = computeDHMessage(p);
	    sendDHMessage(messages);
	    
	    messages = computeDHMessage(q);
	    sendDHMessage(messages);
	    
	    messages = computeDHMessage(yB);
	    sendDHMessage(messages);
	    
	}
	
	
	private static List<byte[]> computeDHMessage(BigInteger n) throws DataSizeException{
		byte[] nBytes = n.toByteArray();
		System.out.println("numero de bytes: "+nBytes.length);
		List<byte[]> res = new ArrayList<byte[]>();
		byte[] code = new byte[129];
		
		if(nBytes.length == 128){
			System.arraycopy(nBytes, 0, code, 1, nBytes.length);
			System.arraycopy("0".getBytes(), 0, code, 0, 1);
			
		}
		else
			code = Arrays.copyOfRange(nBytes, 0, nBytes.length);
		
		res.add(code);
		
		return res;
	}
	
	private static void sendDHMessage(List<byte[]> byteList) throws IOException, DHMessageException{
		
		for(byte[] m: byteList){
			DatagramPacket keysPacket = new DatagramPacket(m, m.length, addr, port);
			System.out.println("ENVIADO: "+keysPacket.getData().length);
			socket.send(keysPacket);
		}
	}
	
	private static BigInteger collectDHValues() throws IOException{
		
		ByteArrayOutputStream aux = new ByteArrayOutputStream();
		
		byte[] keys = new byte[129];
		DatagramPacket keysPacket = new DatagramPacket(keys, keys.length); 
		socket.receive(keysPacket);
		
		aux.write(Arrays.copyOfRange(keysPacket.getData(), 0, keysPacket.getData().length));
		
		return new BigInteger(aux.toByteArray());
	}
	
	private static void generateDHSecretKey() throws IOException{
		
		BigInteger q = collectDHValues();
		System.out.println("q recebido:" +q);
		
		BigInteger yA = collectDHValues();
		
		//sessionKey = yA.modPow(b, q);
		
		//System.out.println(sessionKey);
		
	}
	
	private static void associateCommand(String input) throws Exception {
		
		Message m = new Message(input, phone_number);
		System.out.println("Full Message " + m.getMessage());
		sendEncryptedMessage(m);
		
	}

	private static void sendEncryptedMessage(String input) throws IbanException, AmountException, DataSizeException, NumberFormatException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, InvalidAlgorithmParameterException, IOException{
		String info[] = input.split(" ");
		
		Message m = new Message(info[0], info[1], phone_number);
		
		sendEncryptedMessage(m);
	}
	private static void sendEncryptedMessage(Message m) throws InvalidKeyException, NumberFormatException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, InvalidAlgorithmParameterException, IbanException, AmountException, DataSizeException, IOException {
		
		//generate the iv before exchanging the message.
		generateIV();
		
		//return ; /*
		Long l = m.getID()-2;
		System.out.println("Message Being Sent " + m.getMessage());
		byte[] cypherBytes = cbc.encrypt(new String(m.getMessage().getBytes()));
		DatagramPacket packet = new DatagramPacket(cypherBytes,cypherBytes.length, addr, port);
		
		sendPacket(packet, l);	
	}
	
	private static void sendNonEncryptedMessage(Message m) throws IbanException, AmountException, DataSizeException, InvalidKeyException, NumberFormatException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, InvalidAlgorithmParameterException, IOException {
		m.setKey(sessionKey); // digest stuff
		
		Long l = m.getID()-2;
		byte[] msgBytes = m.getMessage().getBytes();
		System.out.println(m.getMessage());
		System.out.println("NON ENCRYPTED");
		System.out.println(msgBytes);
		DatagramPacket packet = new DatagramPacket(msgBytes,msgBytes.length, addr, port);
		sendPacket(packet, l);
	}
	
	private static void sendPacket(DatagramPacket packet, long id) throws IOException, InvalidKeyException, NoSuchAlgorithmException, NumberFormatException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, InvalidAlgorithmParameterException {
		
		socket.send(packet);
		
		if(!waitAck(id)){
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
	
	private static void generateIV() throws InvalidKeyException, NumberFormatException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, InvalidAlgorithmParameterException, IbanException, AmountException, DataSizeException, IOException{
		
		cbc.generateIV();
		Message m = new Message(cbc.getIV(), true);
		
		sendNonEncryptedMessage(m);
	}
	
	/*
	private static void sendMessage(String input) throws Exception {

		System.out.println("input: "+input.length());
		String info[] = input.split(" ");
	
		Message m = new Message(info[0], info[1], phone_number);
		m.setKey(sessionKey);
		
		Long l = Long.parseLong(new String(m.getMessageBytes()).split("\\|\\|")[1])-2;
		
		byte[] msgBytes = cbc.encrypt(new String(m.getMessageBytes()));
		DatagramPacket packet = new DatagramPacket(msgBytes,msgBytes.length, addr, port);
		
		socket.send(packet);
		
		if(!waitAck(l)){
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
	
	*/
	private static boolean waitAck(Long l) throws IOException, InvalidKeyException, NoSuchAlgorithmException {

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
				/*if(!validateDigest(ackpacket.getData())){
					return false;
				}*/
				
				if(content[0].equals("ack")){
					String client_info = "";
					if(Long.parseLong(content[1]) != l){
						client_info = "Operation not authorized";
						return false;
					}
					System.out.println("Acknowlegde received");
					ackReceived = true;
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
	
	private static boolean confirmIdentity() throws IOException, NoSuchAlgorithmException, NumberFormatException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, InvalidAlgorithmParameterException {
		
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
		/*if(!validateDigest(codePacket.getData())){
			return false;
		}*/		
		
		System.out.println(content[2]);
		Scanner in2 = new Scanner(System.in);
		String input = in2.nextLine();
		char[] code = input.toCharArray();
		Message m = new Message(code);
		m.setKey(sessionKey);
		
		in = in2;
		
		byte[] answer = cbc.encrypt(new String(m.getMessageBytes()));
		
		DatagramPacket answerPacket = new DatagramPacket(answer, answer.length, addr, port);
		socket.send(answerPacket);
		
		Long l = Long.parseLong(new String(m.getMessageBytes()).split("\\|\\|")[1])-2;
		
		if(!waitAck(l)){
			System.out.println("Operation not completed.");
			return false;
		}
		
		return true;
	}
	
	private static boolean validateDigest(byte[] msg) throws NoSuchAlgorithmException, InvalidKeyException{
		int index = new String(msg).lastIndexOf('|');
		byte[] original = Arrays.copyOfRange(msg, index, msg.length);
		byte[] received = calculateDigest(new String(msg).substring(0, index));
		if(Arrays.equals(original, received)){
			return true;
		}
		return false;
	}
	
	private static byte[] calculateDigest(String msg) throws NoSuchAlgorithmException, InvalidKeyException{
		SecretKeySpec keySpec = new SecretKeySpec(sessionKey.getBytes(),"HmacSHA256");
		Mac m = Mac.getInstance("HmacSHA256");
		m.init(keySpec);
		byte[] hash = m.doFinal(msg.getBytes());
		byte[] small = new byte[8];
		small = Arrays.copyOfRange(hash, 0, 8);
		return small;
	}
	
}
