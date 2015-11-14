import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.lang.IllegalArgumentException;
import java.lang.StackTraceElement;


/**
 * SocketASCII is designed for ASCII based line protocols.
 * It uses a java NIO SocketChannel with a ByteBuffer wrapping a fixed size buffer.
 * You need to make sure that the buffer size is bigger than the longest line.
 * (otherwise you will loose some data)
 *
 * There are no charset conversion or copy in temporary buffers.
 * The only copy made is to compact the ByteBuffer as it is not circular.
 *
 * The expected end of lines of ASCII is CRLF (\r\n)
 *
 * You should extend this class in order implement a ASCII base line protocol
 * (cf the example of the NNTP protocol with NntpSocket.java)
 *
 * @author Matthieu Bruel
 * @version 1.0
 *
 * All rights reserved. Relased under terms of the 
 * Creative Commons' Attribution-NonCommercial-ShareAlike license.
 */
public class SocketASCII{

	/** print some debug traces */
	protected static boolean isDebug                = false;

	/** display buffer content */
	protected static boolean dispBuffer             = false;

	/** carriage return byte */
	protected static final byte CR                  = (byte) '\r';

	/** Line Feed byte */
	protected static final byte LF                  = (byte) '\n';

	/** default charset used by getLine to convert bytes in String */
	protected static final String  defaultCharset   = "ISO-8859-15"; // default charset

	/** Size of the byte buffer
	 * /!\ It should be able to contain any line read on the socket /!\
	 */
	protected final int bufferSize;

	/** charset used to convert the byte into String (ISO-8859-15) */
	protected final String charsetEnc;

	/** actual array buffer*/
	protected final byte[] bytesArray;

	/** NIO Bytebuffer that wraps bytesArray*/
	protected final ByteBuffer buffer;

	/** Actual NIO SocketChannel*/
	protected SocketChannel channel;

	/** position of beginning of the current line in the buffer */
	protected int lineStart;

	/** position of end of the current line in the buffer */
	protected int lineEnd;

	/** Total number of bytes read on the socket */
	protected long totalBytesRead;

	/** Host we will connect to */
	protected String host;

	/** Port we will connect to */
	protected int port;

	/** Current line number within the message (response) we are receiving*/
	protected int lineNumber;


	/** Contructor
	 *
	 * @param aBufferSize buffer size (should be bigger than the longest line)
	 */
	public SocketASCII(int aBufferSize){
		// Final attributes have to be initialised in the constructor
		bufferSize     = aBufferSize;
		charsetEnc     = defaultCharset;
		bytesArray     = new byte[bufferSize];
		buffer         = ByteBuffer.wrap(bytesArray);

		// non final attributes initialisation
		init();
	}

	/** Contructor
	 *
	 * @param aBufferSize buffer size (should be bigger than the longest line)
	 * @param aCharsetEnc charset used by getLine to convert the buffer into a String
	 */
	public SocketASCII(int aBufferSize, String aCharsetEnc){
		// Final attributes have to be initialised in the constructor
		bufferSize     = aBufferSize;
		charsetEnc     = aCharsetEnc;
		bytesArray     = new byte[bufferSize];
		buffer         = ByteBuffer.wrap(bytesArray);

		// non final attributes initialisation
		init();
	}

	/** Initialisation of non final attributes */
	private void init(){
		channel        = null;
		lineStart      = 0;
		lineEnd        = 0;
		lineNumber     = 0;
		totalBytesRead = 0;
	}


	/** Open the socket channel and connects to the host
	 *
	 * @param host hostname
	 * @param port port to connect
	 * @return boolean equivalent to SocketChannel.isConnected()
	 */
	public boolean connect(String host, int port){
		InetSocketAddress socketAddress = new InetSocketAddress(host, port);
		try {
			channel = SocketChannel.open();
			channel.connect(socketAddress);
		} catch (IOException e) {
			debug("SocketASCII::connect", "Error connecting", e);
			return false;
		}
		return true;
	}


	/** return the total number of byte read on the socket
	 *
	 * @return total number of byte read on the socket channel
	 */
	public final long getTotalBytesRead(){return totalBytesRead;}


	/** Initialise the buffer for a new reading */
	protected void initRead(){
		// Let's clear the buffer (position to 0 and limit to capacity)
		buffer.clear();

		lineNumber = -1; // -1 so readLine will know it is the first call
		lineEnd    = -1; // So we will process the buffer after first read
		lineStart  = 0;
	}


	/** Read the buffer until we find the end of line
	 *
	 * @return the position of the end of line in the buffer
	 */
	private int getLineEnd() throws BufferUnderflowException {
		byte l = 0;
		while (buffer.hasRemaining()) {
			byte b = buffer.get();
			if (b == LF && l == CR) {
				return buffer.position();
			}
			l = b;
		}
		return -1;
	}


	/** Set lineStart and lineEnd to the position of the next line in the buffer
	 *  - The buffer may contain several lines, we will only point on the next one
	 *  - If a full line is not in the buffer, we will compact it and read on the socket
	 *
	 *  @return true if we could find a line
	 */
	public boolean readLine() throws IOException, IllegalArgumentException{
		// set the start of the line to current position
		lineStart = buffer.position();

		int byteReads;
		do {

			// First call of readLine, we don't process the buffer
			// we will just read from the socket.
			if (lineNumber == -1) {
				lineNumber = 0;

			} else {
				// We look for an end of line in the buffer
				lineEnd = getLineEnd();
				print("SocketASCII::readLine","[getLineEnd]: lineEnd="+lineEnd+", ");
				writeBufState();

				// There is a line in the buffer, we can return
				if (lineEnd != -1) {
					return true;
				}


				// As a line should fit in the buffer, the read on the socket wasn't big enough
				if (lineStart == 0) {
					debug("SocketASCII::readLine", "[SMALL READ ON SOCKET] lineStart == 0");
					buffer.limit(bufferSize);
				}

				// The buffer was finishing with an end of line
				// (we consummed it all, we can just clear it)
				else if (lineStart == buffer.limit()){
					debug("SocketASCII::readLine", "[BUFFER FULLY CONSUMED] lineStart == buffer.limit");
					lineStart = 0;
					buffer.clear(); // (position to 0 and limit to capacity)
				}

				// As the buffer is not circular, we need to compact it:
				// move the element from lineStart to the beginning of the buffer
				// we can then read on the socket to complete the line
				else {
					// set the position to the end of the last line
					buffer.position(lineStart); // throws java.lang.IllegalArgumentException

					print("SocketASCII::readLine", "[COMPACT needed]: from lineStart="+lineStart+", ");
					writeBufState();

					// Compact buffer
					buffer.compact();
					lineStart = 0;

					print("SocketASCII::readLine", "[COMPACT result]: from lineStart="+lineStart+", ");
					writeBufState();
				}

			} // if (lineNumber == -1)

			// Read from the socket
			byteReads = channel.read(buffer);
			totalBytesRead += byteReads;

			print("SocketASCII::readLine", "\n\n[CHANNEL READ] byteReads="+byteReads+", lineEnd: "+lineEnd+", ");
			writeBufState();


			// Let's set the buffer in read more
			buffer.flip();
			if (lineStart != 0) {
				buffer.position(lineStart); // throws java.lang.IllegalArgumentException
			}

			print("SocketASCII::readLine", "[after flip]: ");
			writeBufState();

		} while ((lineEnd == -1) && (byteReads >= 0));

		debug("SocketASCII::readLine", "[readLine] false");
		return false;
	}



	/** Return a string of the current line
	 * /!\ You need to make sure that a read on the socket has been done!
	 *     Otherwise you may end up in an infinite loop /!\
	 *
	 * @return String containing a copy of the current line of the buffer
	 */
	protected String getLine() throws IOException, IllegalArgumentException {
		readLine();
		return new String(buffer.array(), lineStart, lineEnd-lineStart, charsetEnc);
	}


	/** Write a byte buffer on the socket channel
	 *
	 * @param outBuff a ByteBuffer containing the data to write on the socket channel
	 * @return the number of byte written
	 */
	public int write(ByteBuffer outBuff) throws IOException{
		int nbWritten = 0;
		while ( outBuff.hasRemaining() ){
			debug("SocketASCII::write", "writting buffer: "+
					", outBuff, pos: "+outBuff.position()+
					", limit: "+outBuff.limit()+
					"remaining: "+outBuff.remaining());
			nbWritten+=channel.write(outBuff);
		}

		return nbWritten;
	}


	/** Close the socket channel */
	public void close() {
		if ( channel.isConnected() ) {
			try {
				channel.close();
			} catch (IOException e){
				debug("SocketASCII::close", "Error closing channel", e);
			}
		}
	}


	/** Debug function
	 *
	 * @param method method from where we use this debug function
	 * @param str    the message to display
	 */
	public void debug(String method, String str){
		if (isDebug){
			System.out.println("["+method+"] "+str);
		}
	}

	/** Debug function
	 *
	 * @param method    method from where we use this debug function
	 * @param str       the message to display
	 * @param exception the exception to display
	 */
	public void debug(String method, String str, Exception e){
		if (isDebug){
			System.out.println("["+method+"] "+str);
			StackTraceElement[] stack = e.getStackTrace();
			for(StackTraceElement line : stack){
				System.out.println(line.toString());
			}
		}
	}

	/** print function
	 *
	 * @param method method from where we use this print function
	 * @param str    the message to display
	 */
	public void print(String method, String str){
		if (isDebug){
			System.out.print("["+method+"] "+str);
		}
	}

	/** display the state of the buffer (input) */
	public void writeBufState() throws IOException{
		if (isDebug){
			System.out.print("[Buffer]: pos="+buffer.position()
					+", lim=" + buffer.limit()
					+", lineStart=" + lineStart
					+", buff: ");
		}
		if (dispBuffer){
			System.out.write(buffer.array());
		} else if (isDebug){
			System.out.print("\r\n");
		}
	}

}
