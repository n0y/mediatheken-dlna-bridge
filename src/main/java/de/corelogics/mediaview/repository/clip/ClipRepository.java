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

import com.google.inject.Singleton;
import com.netflix.governator.annotations.Configuration;
import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.h2.jdbcx.JdbcConnectionPool;

import javax.annotation.PostConstruct;
import java.io.File;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Singleton
public class ClipRepository {
    private final Logger logger = LogManager.getLogger();

    @Configuration("DATABASE_LOCATION")
    private String databaseLocation;

    private JdbcConnectionPool pool;

    @PostConstruct
    private void initialize() {
        try {
            logger.debug("initializing database at {}", () -> new File(databaseLocation).getAbsolutePath());
            this.pool = JdbcConnectionPool.create(
                    MessageFormat.format("jdbc:h2:{0}", databaseLocation),
                    "sa", "sa");
            logger.info("Successfully opened database at {}", () -> new File(databaseLocation).getAbsolutePath());
            try (var conn = pool.getConnection()) {
                try (var statement = conn.createStatement()) {
                    logger.debug("ensuring table clip exists");
                    statement.execute(
                            "create table if not exists clip (" +
                                    "id varchar(255) primary key, " +
                                    "channelname varchar(255), " +
                                    "containedin varchar(255)," +
                                    "description text," +
                                    "duration varchar(32)," +
                                    "title varchar(255)," +
                                    "url varchar(512)," +
                                    "urlhd varchar(512)," +
                                    "size long," +
                                    "broadcasted_at timestamp with time zone)");
                    logger.debug("ensuring clip indexes exist");
                    statement.execute("create index if not exists idx_clip_channelname on clip(channelname)");
                    statement.execute("create index if not exists idx_clip_containedin on clip(containedin)");
                    statement.execute("create index if not exists idx_clip_broadcasted_at on clip(broadcasted_at)");

                    logger.debug("ensuring table imports exists");
                    statement.execute(
                            "create table if not exists imports(" +
                                    "update_type varchar(30) primary key," +
                                    "import_finished_at timestamp with time zone not null)");
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Could not initialize database", e);
        }
    }

    public Optional<ZonedDateTime> findLastFullImport() {
        logger.debug("finding last full import");
        try (var conn = pool.getConnection()) {
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
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateLastFullImport(ZonedDateTime dateTime) {
        logger.debug("Updating last full import time to {}", dateTime);
        try (var conn = pool.getConnection()) {
            try (var stmt = conn.prepareStatement("merge into imports (update_type, import_finished_at) values (?,?)")) {
                stmt.setString(1, "full");
                stmt.setObject(2, dateTime);
                stmt.execute();
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> findAllChannels() {
        logger.debug("Finding all channels");
        try {
            var channels = new ArrayList<String>();
            try (var conn = pool.getConnection()) {
                try (var stmt = conn.prepareStatement("select channelname, count(*) as clipcount from clip group by channelname order by clipcount desc")) {
                    try (var result = stmt.executeQuery()) {
                        while (result.next()) {
                            var channelName = result.getString(1);
                            logger.debug("Found channel '{}'", channelName);
                            channels.add(channelName);
                        }
                    }
                }
            }
            return channels;
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> findAllContainedIns(String channelName) {
        logger.debug("Finding all containedIns for channel '{}'", channelName);
        try {
            var containedIns = new ArrayList<String>();
            try (var conn = pool.getConnection()) {
                try (var stmt = conn.prepareStatement("select containedin, count(*) as clipcount from clip where channelname=? group by containedin order by containedin asc")) {
                    stmt.setString(1, channelName);
                    try (var result = stmt.executeQuery()) {
                        while (result.next()) {
                            var v = result.getString(1);
                            logger.debug("Found containedIn '{}'", v);
                            containedIns.add(v);
                        }
                    }
                }
            }
            return containedIns;
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> findAllContainedIns(String channelName, String startingWith) {
        logger.debug("Finding all containedIns for channel '{}' starting with '{}'", channelName, startingWith);
        try {
            var containedIns = new ArrayList<String>();
            try (var conn = pool.getConnection()) {
                try (var stmt = conn.prepareStatement("select containedin, count(*) as clipcount from clip where channelname=? and lower(containedin) like ? group by containedin order by containedin asc")) {
                    stmt.setString(1, channelName);
                    stmt.setString(2, startingWith.toLowerCase(Locale.GERMAN) + "%");
                    try (var result = stmt.executeQuery()) {
                        while (result.next()) {
                            var v = result.getString(1);
                            logger.debug("found containedIn '{}'", v);
                            containedIns.add(v);
                        }
                    }
                }
            }
            return containedIns;
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public void addClips(Iterable<ClipEntry> clipEntries) {
        logger.debug("Adding ClipEntries");
        try {
            try (var conn = pool.getConnection()) {
                try (var stmt = conn.prepareStatement(
                        "merge into clip (id, channelname, containedin, description, duration, title, url, urlhd, size, broadcasted_at) " +
                                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (var c : clipEntries) {
                        logger.debug("Adding ClipEntry {}", c);
                        stmt.setString(1, c.getChannelName() + ":" + c.getBroadcastedAt().toEpochSecond());
                        stmt.setString(2, strip(c.getChannelName()));
                        stmt.setString(3, strip(c.getContainedIn()));
                        stmt.setString(4, strip(c.getDescription()));
                        stmt.setString(5, c.getDuration());
                        stmt.setString(6, strip(c.getTitle()));
                        stmt.setString(7, c.getUrl());
                        stmt.setString(8, c.getUrlHd());
                        stmt.setLong(9, c.getSize());
                        stmt.setObject(10, c.getBroadcastedAt());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();

                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String strip(String in) {
        if (in.length() < 254) {
            return in;
        }
        return in.substring(0, 254);
    }

    public List<ClipEntry> listClips(String channelId, String containedIn) {
        logger.debug("Finding all clips for channel '{}' and containedIn '{}'", channelId, containedIn);
        try {
            var list = new ArrayList<ClipEntry>();
            try (var conn = pool.getConnection()) {
                try (var stmt = conn.prepareStatement("select title, broadcasted_at, description, size, url, urlhd, duration from clip where channelname=? and containedin=? order by broadcasted_at desc")) {
                    stmt.setString(1, channelId);
                    stmt.setString(2, containedIn);
                    try (var result = stmt.executeQuery()) {
                        while (result.next()) {
                            var v = new ClipEntry(
                                    result.getString(1),
                                    containedIn,
                                    result.getObject(2, ZonedDateTime.class),
                                    result.getString(3),
                                    channelId,
                                    result.getLong(4),
                                    result.getString(5),
                                    result.getString(6),
                                    result.getString(7));
                            logger.debug("Found clipEntry {}", v);
                            list.add(v);
                        }
                    }
                }
            }
            return list;
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
