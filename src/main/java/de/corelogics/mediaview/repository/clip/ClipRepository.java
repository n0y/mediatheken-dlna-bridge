/*
 * MIT License
 *
 * Copyright (c) 2020 corelogics.de
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.corelogics.mediaview.repository.clip;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.inject.Singleton;
import com.netflix.governator.annotations.Configuration;
import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.h2.jdbcx.JdbcConnectionPool;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.UTF_8;

@Singleton
public class ClipRepository {
	@FunctionalInterface
	private interface SqlFunction<T> {
		T execute(Connection conn) throws SQLException;
	}

	private static final Base64.Encoder BASE64ENC = Base64.getEncoder().withoutPadding();

	private static final HashFunction HASHING = Hashing.sipHash24();

	private final Logger logger = LogManager.getLogger();

	private ReadWriteLock dbLock = new ReentrantReadWriteLock();

	@Configuration("DATABASE_LOCATION")
	String databaseLocation;

	Supplier<Long> maxMemorySupplier = Runtime.getRuntime()::maxMemory;

	private JdbcConnectionPool pool;

	@PostConstruct
	void openAndInitialize() {
		dbLock.writeLock().lock();
		try {
			openConnection(calcJdbcUrl(calcCacheSize()));
			initialize();
			logger.info("Successfully opened database at {} with {}",
					() -> null == databaseLocation ? "<in-mem>" : new File(databaseLocation).getAbsolutePath(),
					() -> calcJdbcUrl(calcCacheSize()));
		} finally {
			dbLock.writeLock().unlock();
		}
	}

	private <T> T withConnection(SqlFunction<T> function) {
		dbLock.readLock().lock();
		try (var conn = pool.getConnection()) {
			return function.execute(conn);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			dbLock.readLock().unlock();
		}
	}

	public void compact() {
		dbLock.writeLock().lock();
		try {
			try (var conn = pool.getConnection()) {
				try (var stmt = conn.createStatement()) {
					stmt.execute("shutdown compact");
				}
			}
			this.pool.dispose();
			openConnection(calcJdbcUrl(calcCacheSize()));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			dbLock.writeLock().unlock();
		}
	}

	String calcJdbcUrl(long cacheSize) {
		logger.debug("initializing database at {}",
				() -> null == databaseLocation ? "<in-mem>" : new File(databaseLocation).getAbsolutePath());
		return String.format("jdbc:h2:%s;CACHE_SIZE=%d", databaseLocation, cacheSize / 1024);
	}

	long calcCacheSize() {
		return Math.min(Math.max(16_000_000L, maxMemorySupplier.get() - 150_000_000), 1_500_000_000L);
	}

	void initialize() {
		withConnection(conn -> {
			try (var statement = conn.createStatement()) {
				logger.debug("ensuring table clip exists");
				statement.execute(
						"create table if not exists clip (" +
								"id varchar(255) primary key, " +
								"channelname varchar(255), " +
								"channelname_lo varchar(255) as lower(channelname)," +
								"containedin varchar(255)," +
								"containedin_lo varchar(255) as lower(containedin)," +
								"duration varchar(32)," +
								"title varchar(255)," +
								"title_lo varchar(255) as lower(title)," +
								"url_normal varchar(512)," +
								"urlhd varchar(512)," +
								"size long," +
								"broadcasted_at timestamp with time zone," +
								"imported_at timestamp with time zone)");
				statement.execute("alter table clip drop column if exists description");

				logger.debug("ensuring clip indexes exist");
				statement.execute("create index if not exists idx_clip_channelname on clip(channelname)");
				statement.execute("create index if not exists idx_clip_channelname_lo on clip(channelname_lo)");
				statement.execute("create index if not exists idx_clip_containedin on clip(containedin)");
				statement.execute("create index if not exists idx_clip_containedin_lo on clip(containedin_lo)");
				statement.execute("create index if not exists idx_clip_broadcasted_at on clip(broadcasted_at)");
				statement.execute("create index if not exists idx_clip_imported_at on clip(imported_at)");
				statement.execute("create index if not exists idx_clip_chanwithlocontainer on clip(channelname_lo,containedin)");
				statement.execute("create index if not exists idx_clip_chanlowithcontainerlo on clip(channelname_lo, containedin_lo)");

				logger.debug("ensuring table imports exists");
				statement.execute(
						"create table if not exists imports(" +
								"update_type varchar(30) primary key," +
								"import_finished_at timestamp with time zone not null)");

				statement.execute("analyze table clip");
			}
			return true;
		});
	}

	void openConnection(String jdbcUrl) {
		this.pool = JdbcConnectionPool.create(jdbcUrl, "sa", "sa");
	}

	@PreDestroy
	void destroy() {
		this.pool.dispose();
	}

	public Optional<ZonedDateTime> findLastFullImport() {
		logger.debug("finding last full import");
		return withConnection(conn -> {
			try (var stmt = conn.prepareStatement("select import_finished_at from imports where update_type=?")) {
				stmt.setString(1, "full");
				try (var res = stmt.executeQuery()) {
					if (res.next()) {
						var v = Optional.of(res.getObject(1, ZonedDateTime.class));
						logger.debug("Found last full import: finished at {}", v::get);
						return v;
					}
				}
			}
			logger.debug("No last full import found in db");
			return Optional.empty();
		});
	}

	public void updateLastFullImport(ZonedDateTime dateTime) {
		logger.debug("Updating last full import time to {}", dateTime);
		withConnection(conn -> {
			try (var stmt = conn.prepareStatement("merge into imports (update_type, import_finished_at) values (?,?)")) {
				stmt.setString(1, "full");
				stmt.setObject(2, dateTime);
				stmt.execute();
			}
			try (var stmt = conn.createStatement()) {
				stmt.execute("analyze table clip");
			}
			return true;
		});
	}

	public List<String> findAllChannels() {
		logger.debug("Finding all channels");
		var channels = new ArrayList<String>();
		return withConnection(conn -> {
			try (var stmt = conn
					.prepareStatement("select channelname, count(*) as clipcount from clip group by channelname order by clipcount desc")) {
				try (var result = stmt.executeQuery()) {
					while (result.next()) {
						var channelName = result.getString(1);
						logger.debug("Found channel '{}'", channelName);
						channels.add(channelName);
					}
				}
			}
			return channels;
		});
	}

	/**
	 * @return name to number of clips
	 */
	public Map<String, Integer> findAllContainedIns(String channelName) {
		logger.debug("Finding all containedIns for channel '{}'", channelName);
		return withConnection(conn -> {
			var containedIns = new HashMap<String, Integer>();
			try (var stmt = conn
					.prepareStatement("select containedin, count(*) as clipcount from clip where channelname_lo=? group by containedin")) {
				stmt.setString(1, channelName.toLowerCase(Locale.GERMANY));
				try (var result = stmt.executeQuery()) {
					while (result.next()) {
						var name = result.getString(1);
						var count = result.getInt(2);
						logger.debug("Found containedIn '{}' ({} clips)", name, count);
						containedIns.put(name, count);
					}
				}
			}
			return containedIns;
		});
	}

	/**
	 * @return name to number of clips
	 */
	public Map<String, Integer> findAllContainedIns(String channelName, String startingWith) {
		logger.debug("Finding all containedIns for channel '{}' starting with '{}'", channelName, startingWith);
		return withConnection(conn -> {
			var containedIns = new HashMap<String, Integer>();
			try (var stmt = conn.prepareStatement(
					"select containedin, count(*) as clipcount from clip where channelname_lo=? and containedin_lo like ? group by containedin")) {
				stmt.setString(1, channelName.toLowerCase(Locale.GERMANY));
				stmt.setString(2, startingWith.toLowerCase(Locale.GERMANY) + "%");
				try (var result = stmt.executeQuery()) {
					while (result.next()) {
						var name = result.getString(1);
						var count = result.getInt(2);
						logger.debug("Found containedIn '{}' ({} clips)", name, count);
						containedIns.put(name, count);
					}
				}
			}
			return containedIns;
		});
	}

	public void addClips(Iterable<ClipEntry> clipEntries, ZonedDateTime importedAt) {
		logger.debug("Adding ClipEntries");
		withConnection(conn -> {
			try (var stmt = conn.prepareStatement(
					"merge into clip (id, channelname, containedin, duration, title, url_normal, urlhd, size, broadcasted_at, imported_at) "
							+
							"values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
				for (var c : clipEntries) {
					logger.debug("Adding ClipEntry {}", c);
					stmt.setString(1, createId(c));
					stmt.setString(2, limitShort(c.getChannelName()));
					stmt.setString(3, limitShort(c.getContainedIn()));
					stmt.setString(4, c.getDuration());
					stmt.setString(5, limitShort(c.getTitle()));
					stmt.setString(6, c.getUrl());
					stmt.setString(7, c.getUrlHd());
					stmt.setLong(8, c.getSize());
					stmt.setObject(9, c.getBroadcastedAt());
					stmt.setObject(10, importedAt);
					stmt.addBatch();
				}
				stmt.executeBatch();
			}
			return true;
		});
	}

	private String limitShort(String in) {
		return limit(in, 254);
	}

	private String limitLong(String in) {
		return limit(in, 4000);
	}

	private String limit(String in, int length) {
		if (in.length() < length) {
			return in;
		}
		return in.substring(0, 254);
	}

	public List<ClipEntry> findAllClips(String channelId, String containedIn) {
		logger.debug("Finding all clips for channel '{}' and containedIn '{}'", channelId, containedIn);
		return withConnection(conn -> {
			var list = new ArrayList<ClipEntry>();
			try (var stmt = conn.prepareStatement(
					"select title, broadcasted_at, size, url_normal, urlhd, duration from clip where channelname_lo=? and containedin_lo=? order by broadcasted_at desc, char_length(title) asc")) {
				stmt.setString(1, channelId.toLowerCase(Locale.GERMAN));
				stmt.setString(2, containedIn.toLowerCase(Locale.GERMAN));
				try (var result = stmt.executeQuery()) {
					while (result.next()) {
						var v = new ClipEntry(
								channelId, containedIn,
								result.getObject("broadcasted_at", ZonedDateTime.class),
								result.getString("title"),
								result.getString("duration"),
								result.getLong("size"),
								result.getString("url_normal"),
								result.getString("urlhd")
						);
						logger.debug("Found clipEntry {}", v);
						list.add(v);
					}
				}
			}
			return list;
		});
	}

	public List<ClipEntry> findAllClipsForChannelBetween(String channelName, ZonedDateTime startDate, ZonedDateTime endDate) {
		return withConnection(conn -> {
			var ret = new ArrayList<ClipEntry>();
			try (var stmt = conn.prepareStatement(
					"select * from clip where channelname_lo = ? and broadcasted_at >= ? and broadcasted_at <= ? order by broadcasted_at asc, char_length(title) asc")) {
				stmt.setString(1, channelName.toLowerCase(Locale.GERMANY));
				stmt.setObject(2, startDate);
				stmt.setObject(3, endDate);
				try (var result = stmt.executeQuery()) {
					while (result.next()) {
						ret.add(new ClipEntry(
								channelName,
								result.getString("containedIn"),
								result.getObject("broadcasted_at", ZonedDateTime.class),
								result.getString("title"),
								result.getString("duration"),
								result.getLong("size"),
								result.getString("url_normal"),
								result.getString("urlhd")));
					}
				}
			}
			return ret;
		});
	}


	public String createId(ClipEntry clipEntry) {
		return BASE64ENC.encodeToString(
				HASHING.newHasher()
						.putString(clipEntry.getChannelName(), UTF_8)
						.putString(clipEntry.getBestUrl(), UTF_8)
						.hash().asBytes());
	}

	public void deleteClipsImportedBefore(ZonedDateTime startedAt) {
		logger.debug("Deleting all clips not imported at {}", startedAt);
		withConnection(conn -> {
			try (var stmt = conn.prepareStatement("delete from clip where imported_at < ?")) {
				stmt.setObject(1, startedAt);
				var numDeleted = stmt.executeUpdate();
				logger.debug("Deleted {} clips not imported at {}", numDeleted, startedAt);
			}
			return true;
		});
	}
}
