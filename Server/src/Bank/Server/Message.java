package Bank.Server;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import Bank.Server.Exceptions.AmountException;
import Bank.Server.Exceptions.IbanException;

public class Message {
	
	private static byte[] Confirmation_Ack = 	new byte[] {0,0};	//transfer completed
	private static byte[] Amount_Error_Ack = 	new byte[] {1,1};	//amount not available 
	private static byte[] Source_Unknown_Ack = new byte[] {1,0};		//source iban not found
	private static byte[] Dest_Unknown_Ack = 	new byte[] {0,1};	//dest iban not fount
	private static byte[] Not_Autorized_Ack = new byte[] {2,0};		//source iban doesnt belong to that port
	
	private long ID;
	private String type;
	private String data;
	private Date timestamp;
	
	private static SecureRandom randomizer = new SecureRandom(); 

	//Association command
	public Message(String iban) throws IbanException{ 
		this.ID =  new BigInteger(64, randomizer).longValue();
		this.type = "associate";
		setIban(iban);
		this.timestamp = new Date();
	}
	
	//Send command
	public Message(String iban, String amount) throws IbanException, AmountException{
		this.ID =  new BigInteger(64, randomizer).longValue();
		this.type = "send";
		setIban(iban);
		setAmount(amount);
		this.timestamp = new Date();
	}
	
	
	//Client send code of matrix card
	public Message(char[] code){
		this.ID =  new BigInteger(64, randomizer).longValue();
		this.type = "codes_answer";
		this.data = new String(code);
		this.timestamp = new Date();
	}
	
	
	//Acknowledge message
	public Message(byte[] ack){
		this.type = "ack";
		setAck(ack);
		this.timestamp = new Date();
	}
	
	//Server sends positions of matrix card
	public Message(int[] a, int[] b, int[] c, int[] d){
		this.ID =  new BigInteger(64, randomizer).longValue();
		this.type = "codes";
		setCodes(a,b,c,d);
		this.timestamp = new Date();
	}
	
	private void setCodes(int[] a, int[] b, int[] c, int[] d) {
		String line = ":column_" + (a[0]+1) + "line_" + (a[1]+1) + ":";
		line += "column_" + (b[0]+1) + "_line_" + (b[1]+1) + ":";
		line += "column_" + (c[0]+1) + "_line_" + (c[1]+1) + ":";
		line += "column_" + (d[0]+1) + "_line_" + (d[1]+1) + ":";
		this.data = line;
	}
	
	private void setAck(byte[] ack) {
		if(Arrays.equals(ack, Confirmation_Ack)){
			this.data = "confirmed";
		}else if(Arrays.equals(ack, Amount_Error_Ack)){
			this.data = "amount_error";
		}else if(Arrays.equals(ack, Dest_Unknown_Ack)){
			this.data = "destination_unknown";
		}else if(Arrays.equals(ack, Source_Unknown_Ack)){
			this.data = "source_unknown";
		}else if(Arrays.equals(ack, Not_Autorized_Ack)){
			this.data = "not_authorized";
		}
	}

	private void setIban(String iban) throws IbanException{
		if(verifyIban(iban)){
			this.data = iban;
		}
		else{
			throw new IbanException(iban);
		}
	}
	
	private void setAmount(String amount) throws AmountException{
		if(verifyAmount(amount)){
			this.data += " " + amount;
		}
		else{
			throw new AmountException(amount);
		}
	}
	
	private boolean verifyIban(String iban){
		if(iban.length() == 25){
			String letters = iban.substring(0, 1);
			String numbers = iban.substring(2,iban.length());
			if(letters.matches("[A-Z]+") && numbers.matches("[0-9]+"))
				return true;
		}
		return false;
	}
	
	private boolean verifyAmount(String amount){
		long value = Long.parseLong(amount);
		if(value > 0 && amount.length()<=8)
			return true;
		return false;
	}
	
	protected void setID(long id){
		this.ID = id;
	}
	
	private long getID(){
		return this.ID;
	}
	
	private String getType(){
		return this.type;
	}
	
	private String getData(){
		return this.data;
	}
	
	private String getTimeStamp(){
		SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return dt.format(this.timestamp);
	}
	
	public String getMessage(){
		return getType() + "||" + getID() + "||" + getData() + "||" + getTimeStamp();
	}
}
