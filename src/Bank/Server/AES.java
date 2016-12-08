package Bank.Server;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

public class AES {
	
	int _keyLength;
	SecretKey _key;
	byte[] _iv = { 0, 1 , 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
	
	public AES(String key) throws UnsupportedEncodingException{
		_keyLength = 256;
		setKey(key);
		//setIV(iv);
		
	}
	
	public byte[] getIV(){ return _iv;}
	
	private void setKey(String key) throws UnsupportedEncodingException{
		byte[] keyBytes = new byte[_keyLength/8];
		Arrays.fill(keyBytes, (byte) 0x0);
		byte[] passwordBytes = key.getBytes("UTF-8");
		int length = passwordBytes.length < keyBytes.length ? passwordBytes.length : keyBytes.length;
		
		System.arraycopy(passwordBytes, 0, keyBytes, 0, length);
		_key = new SecretKeySpec(keyBytes, "AES");
		
	}
	
	private void setIV(byte[] iv){ _iv = iv;}

	
	public byte[] generateIV(){
		
		/**
		 * Step 2. Generate an Initialization Vector (IV) 
		 * 		a. Use SecureRandom to generate random bits
		 * 		   The size of the IV matches the blocksize of the cipher (128 bits for AES)
		 * 		b. Construct the appropriate IvParameterSpec object for the data to pass to Cipher's init() method
		 */

		final int AES_KEYLENGTH = _keyLength;	// change this as desired for the security level you want
		byte[] iv = new byte[AES_KEYLENGTH / 8];	// Save the IV bytes or send it in plaintext with the encrypted data so you can decrypt the data later
		SecureRandom prng = new SecureRandom();
		prng.nextBytes(iv);
		
		return iv;
	}
	
	private Cipher createCipher() throws NoSuchAlgorithmException, NoSuchPaddingException{
		/**
		 * Step 3. Create a Cipher by specifying the following parameters
		 * 		a. Algorithm name - here it is AES 
		 * 		b. Mode - here it is CBC mode 
		 * 		c. Padding - e.g. PKCS7 or PKCS5
		 */

		Cipher aesCipherForEncryption = Cipher.getInstance("AES/CBC/PKCS5PADDING"); // Must specify the mode explicitly as most JCE providers default to ECB mode!!
		
		return aesCipherForEncryption;
	}
	
	public String encrypt(String strDataToEncrypt) throws IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, UnsupportedEncodingException{
		
		String strCipherText = new String();
		
		/**
		 * Step 4. Initialize the Cipher for Encryption
		 */
		
		Cipher aesCipherForEncryption = createCipher();
		
		aesCipherForEncryption.init(Cipher.ENCRYPT_MODE, _key, new IvParameterSpec(_iv));

		/**
		 * Step 5. Encrypt the Data 
		 * 		a. Declare / Initialize the Data. Here the data is of type String 
		 * 		b. Convert the Input Text to Bytes 
		 * 		c. Encrypt the bytes using doFinal method
		 */
		
		byte[] byteDataToEncrypt = strDataToEncrypt.getBytes("UTF-8");
		byte[] byteCipherText = aesCipherForEncryption.doFinal(byteDataToEncrypt);
		System.out.println(new String(byteCipherText));
		// b64 is done differently on Android
		strCipherText= new BASE64Encoder().encode(byteCipherText);
	
		System.out.println("Cipher Text generated using AES is "+ strCipherText);
		
		return strCipherText;
	}
	
	public String decrypt(String encryptedString) throws InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException, IOException{
		
		String strDecryptedText = new String();
		byte[] byteCipherText = new BASE64Decoder().decodeBuffer(encryptedString);
		System.out.println(new String(byteCipherText));
		
		/**
		 * Step 6. Decrypt the Data 
		 * 		a. Initialize a new instance of Cipher for Decryption (normally don't reuse the same object)
		 * 		   Be sure to obtain the same IV bytes for CBC mode.
		 * 		b. Decrypt the cipher bytes using doFinal method
		 */

		Cipher aesCipherForDecryption = createCipher();
		
		aesCipherForDecryption.init(Cipher.DECRYPT_MODE, _key,new IvParameterSpec(_iv));
		byte[] byteDecryptedText = aesCipherForDecryption.doFinal(byteCipherText);
		strDecryptedText = new String(byteDecryptedText, "UTF-8");
		System.out.println(" Decrypted Text message is " + strDecryptedText);
		
		return strDecryptedText;
	}
}
