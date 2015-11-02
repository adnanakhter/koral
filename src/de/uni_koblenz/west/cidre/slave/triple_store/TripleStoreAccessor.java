package de.uni_koblenz.west.cidre.slave.triple_store;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import de.uni_koblenz.west.cidre.common.config.impl.Configuration;
import de.uni_koblenz.west.cidre.common.query.Mapping;
import de.uni_koblenz.west.cidre.common.query.MappingRecycleCache;
import de.uni_koblenz.west.cidre.common.query.TriplePattern;
import de.uni_koblenz.west.cidre.slave.triple_store.impl.MapDBTripleStore;

public class TripleStoreAccessor implements Closeable, AutoCloseable {

	@SuppressWarnings("unused")
	private final Logger logger;

	private final MapDBTripleStore tripleStore;

	public TripleStoreAccessor(Configuration conf, Logger logger) {
		this.logger = logger;
		tripleStore = new MapDBTripleStore(conf.getTripleStoreStorageType(),
				conf.getTripleStoreDir(), conf.useTransactionsForTripleStore(),
				conf.isTripleStoreAsynchronouslyWritten(),
				conf.getTripleStoreCacheType());
	}

	public void storeTriples(File file) {
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(
				new GZIPInputStream(new FileInputStream(file))));) {
			long subject = in.readLong();
			long property = in.readLong();
			long object = in.readLong();
			short length = in.readShort();
			byte[] containment = new byte[length];
			in.readFully(containment);
			tripleStore.storeTriple(subject, property, object, containment);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Iterable<Mapping> lookup(MappingRecycleCache cache,
			TriplePattern triplePattern) {
		return tripleStore.lookup(cache, triplePattern);
	}

	public void clear() {
		tripleStore.clear();
	}

	@Override
	public void close() {
		tripleStore.close();
	}

}
