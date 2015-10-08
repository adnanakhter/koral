package de.uni_koblenz.west.cidre.master.client_manager;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.PriorityQueue;
import java.util.logging.Logger;

import de.uni_koblenz.west.cidre.common.messages.MessageType;

public class FileReceiver implements Closeable {

	public static final int NUMBER_OF_PARALLELY_REQUESTED_FILE_CHUNKS = 10;

	private final Logger logger;

	private final int clientID;

	private final ClientConnectionManager clientConnections;

	private final File workingDir;

	private final int totalNumberOfFiles;

	private int currentFile;

	private OutputStream out;

	private final PriorityQueue<FileChunk> unprocessedChunks;

	// TODO handle connection terminations;

	public FileReceiver(File workingDir, int clientID,
			ClientConnectionManager clientConnections, int numberOfFiles,
			Logger logger) {
		this.workingDir = workingDir;
		this.clientID = clientID;
		this.clientConnections = clientConnections;
		totalNumberOfFiles = numberOfFiles;
		this.logger = logger;
		unprocessedChunks = new PriorityQueue<>(
				NUMBER_OF_PARALLELY_REQUESTED_FILE_CHUNKS);
	}

	public void requestFiles() {
		currentFile = -1;
		requestNextFile();
	}

	private void requestNextFile() {
		if (out != null) {
			try {
				out.close();
			} catch (IOException e) {
				logger.throwing(e.getStackTrace()[0].getClassName(),
						e.getStackTrace()[0].getMethodName(), e);
			}
		}
		currentFile++;
		if (currentFile < totalNumberOfFiles) {
			try {
				out = new BufferedOutputStream(new FileOutputStream(
						new File(workingDir.getAbsolutePath()
								+ File.separatorChar + currentFile)));
			} catch (FileNotFoundException e) {
				logger.throwing(e.getStackTrace()[0].getClassName(),
						e.getStackTrace()[0].getMethodName(), e);
			}
		}
		requestNextFileChunk();
	}

	private void requestNextFileChunk() {
		FileChunk chunk = unprocessedChunks.peek();
		if (chunk == null) {
			chunk = new FileChunk(currentFile, 0);
			unprocessedChunks.add(chunk);
			requestFileChunk(chunk);
		} else if (!chunk.isReceived()) {
			// there are file chunks that have not been received yet
			// request them again
			requestFileChunk(chunk);
		}
		while (unprocessedChunks
				.size() < NUMBER_OF_PARALLELY_REQUESTED_FILE_CHUNKS) {
			if (chunk.isLastChunk()) {
				break;
			}
			chunk = new FileChunk(currentFile, chunk.getSequenceNumber() + 1);
			unprocessedChunks.add(chunk);
			requestFileChunk(chunk);
		}
	}

	private void requestFileChunk(FileChunk chunk) {
		byte[] request = new byte[1 + 4 + 8];
		request[0] = MessageType.REQUEST_FILE_CHUNK.getValue();
		byte[] fileID = ByteBuffer.allocate(4).putInt(currentFile).array();
		System.arraycopy(fileID, 0, request, 1, fileID.length);
		byte[] chunkID = ByteBuffer.allocate(8)
				.putLong(chunk.getSequenceNumber()).array();
		System.arraycopy(chunkID, 0, request, 5, chunkID.length);
		clientConnections.send(clientID, request);
	}

	public void receiveFileChunk(int fileID, long chunkID,
			long totalNumberOfChunks, byte[] chunkContent) {
		// TODO Auto-generated method stub
		// write and remove all initial file chunks
		// remove all chunks with invalid fileIDs
		// request further graph chunks
		// request next file
	}

	public boolean isFinished() {
		return currentFile > totalNumberOfFiles - 1;
	}

	@Override
	public void close() {
		if (out != null) {
			try {
				out.close();
			} catch (IOException e) {
				logger.throwing(e.getStackTrace()[0].getClassName(),
						e.getStackTrace()[0].getMethodName(), e);
			}
		}
	}

}