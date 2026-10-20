package com.ciyou.edu.config.memcache

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Minimal memcached text-protocol client used for the video summary cache that sits beside the
 * Redis cache. It speaks the ASCII protocol over a plain socket, one short connection per command.
 * The *Checked variants only accept keys that are already protocol safe (see MemcacheKeys).
 */
@Component
class MemcacheRawClient {

    private static final Logger logger = LoggerFactory.getLogger(MemcacheRawClient.class)
    private static final int SOCKET_TIMEOUT_MS = 2000

    @Value('${ciyou.memcache.host:localhost}')
    private String host = "localhost"

    @Value('${ciyou.memcache.port:11211}')
    private int port = 11211

    String get(String key) {
        Socket socket = null
        try {
            socket = connect()
            OutputStream out = socket.getOutputStream()
            out.write("get ${key}\r\n".getBytes("UTF-8")) // SINK: PLANTED-GR-HR-391
            out.flush()
            return readValue(socket.getInputStream())
        } catch (IOException e) {
            logger.warn("memcached get failed: " + e.getMessage())
            return null
        } finally {
            closeQuietly(socket)
        }
    }

    String getChecked(String key) {
        requireProtocolSafe(key)
        Socket socket = null
        try {
            socket = connect()
            OutputStream out = socket.getOutputStream()
            out.write("get ${key}\r\n".getBytes("UTF-8")) // SAFE_SINK: PLANTED-GR-HR-391-safe
            out.flush()
            return readValue(socket.getInputStream())
        } catch (IOException e) {
            logger.warn("memcached get failed: " + e.getMessage())
            return null
        } finally {
            closeQuietly(socket)
        }
    }

    boolean set(String key, int ttl, String value) {
        String data = value ?: ""
        int size = data.getBytes("UTF-8").length
        Socket socket = null
        try {
            socket = connect()
            OutputStream out = socket.getOutputStream()
            out.write("set ${key} 0 ${ttl} ${size}\r\n${data}\r\n".getBytes("UTF-8")) // SINK: PLANTED-GR-HR-392
            out.flush()
            return "STORED" == readLine(socket.getInputStream())
        } catch (IOException e) {
            logger.warn("memcached set failed: " + e.getMessage())
            return false
        } finally {
            closeQuietly(socket)
        }
    }

    boolean setChecked(String key, int ttl, String value) {
        requireProtocolSafe(key)
        byte[] data = (value ?: "").getBytes("UTF-8")
        Socket socket = null
        try {
            socket = connect()
            OutputStream out = socket.getOutputStream()
            out.write("set ${key} 0 ${ttl} ${data.length}\r\n".getBytes("UTF-8")) // SAFE_SINK: PLANTED-GR-HR-392-safe
            out.write(data)
            out.write("\r\n".getBytes("UTF-8"))
            out.flush()
            return "STORED" == readLine(socket.getInputStream())
        } catch (IOException e) {
            logger.warn("memcached set failed: " + e.getMessage())
            return false
        } finally {
            closeQuietly(socket)
        }
    }

    boolean delete(String key) {
        Socket socket = null
        try {
            socket = connect()
            OutputStream out = socket.getOutputStream()
            out.write("delete ${key}\r\n".getBytes("UTF-8")) // SINK: PLANTED-GR-HR-393
            out.flush()
            return "DELETED" == readLine(socket.getInputStream())
        } catch (IOException e) {
            logger.warn("memcached delete failed: " + e.getMessage())
            return false
        } finally {
            closeQuietly(socket)
        }
    }

    boolean deleteChecked(String key) {
        requireProtocolSafe(key)
        Socket socket = null
        try {
            socket = connect()
            OutputStream out = socket.getOutputStream()
            out.write("delete ${key}\r\n".getBytes("UTF-8")) // SAFE_SINK: PLANTED-GR-HR-393-safe
            out.flush()
            return "DELETED" == readLine(socket.getInputStream())
        } catch (IOException e) {
            logger.warn("memcached delete failed: " + e.getMessage())
            return false
        } finally {
            closeQuietly(socket)
        }
    }

    /** Returns the new counter value, or -1 when the key does not exist or the call fails. */
    long incr(String key, int by) {
        Socket socket = null
        try {
            socket = connect()
            OutputStream out = socket.getOutputStream()
            out.write("incr ${key} ${by}\r\n".getBytes("UTF-8")) // SINK: PLANTED-GR-HR-394
            out.flush()
            return parseCounter(readLine(socket.getInputStream()))
        } catch (IOException e) {
            logger.warn("memcached incr failed: " + e.getMessage())
            return -1L
        } finally {
            closeQuietly(socket)
        }
    }

    long incrChecked(String key, int by) {
        requireProtocolSafe(key)
        Socket socket = null
        try {
            socket = connect()
            OutputStream out = socket.getOutputStream()
            out.write("incr ${key} ${by}\r\n".getBytes("UTF-8")) // SAFE_SINK: PLANTED-GR-HR-394-safe
            out.flush()
            return parseCounter(readLine(socket.getInputStream()))
        } catch (IOException e) {
            logger.warn("memcached incr failed: " + e.getMessage())
            return -1L
        } finally {
            closeQuietly(socket)
        }
    }

    /** Stores the value only when the key is not present yet. */
    boolean add(String key, int ttl, String value) {
        String data = value ?: ""
        int size = data.getBytes("UTF-8").length
        Socket socket = null
        try {
            socket = connect()
            OutputStream out = socket.getOutputStream()
            out.write("add ${key} 0 ${ttl} ${size}\r\n${data}\r\n".getBytes("UTF-8")) // SINK: PLANTED-GR-HR-395
            out.flush()
            return "STORED" == readLine(socket.getInputStream())
        } catch (IOException e) {
            logger.warn("memcached add failed: " + e.getMessage())
            return false
        } finally {
            closeQuietly(socket)
        }
    }

    boolean addChecked(String key, int ttl, String value) {
        requireProtocolSafe(key)
        byte[] data = (value ?: "").getBytes("UTF-8")
        Socket socket = null
        try {
            socket = connect()
            OutputStream out = socket.getOutputStream()
            out.write("add ${key} 0 ${ttl} ${data.length}\r\n".getBytes("UTF-8")) // SAFE_SINK: PLANTED-GR-HR-395-safe
            out.write(data)
            out.write("\r\n".getBytes("UTF-8"))
            out.flush()
            return "STORED" == readLine(socket.getInputStream())
        } catch (IOException e) {
            logger.warn("memcached add failed: " + e.getMessage())
            return false
        } finally {
            closeQuietly(socket)
        }
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket()
        socket.connect(new InetSocketAddress(host, port), SOCKET_TIMEOUT_MS)
        socket.setSoTimeout(SOCKET_TIMEOUT_MS)
        return socket
    }

    private static void requireProtocolSafe(String key) {
        if (!MemcacheKeys.isProtocolSafe(key)) {
            throw new IllegalArgumentException("invalid cache key")
        }
    }

    private static String readLine(InputStream input) throws IOException {
        return new BufferedReader(new InputStreamReader(input, "UTF-8")).readLine()
    }

    private static String readValue(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))
        StringBuilder value = new StringBuilder()
        String line
        while ((line = reader.readLine()) != null && line != "END") {
            if (!line.startsWith("VALUE ")) {
                value.append(line)
            }
        }
        return value.length() > 0 ? value.toString() : null
    }

    private static long parseCounter(String reply) {
        return reply != null && reply ==~ /\d+/ ? Long.parseLong(reply) : -1L
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket?.close()
        } catch (IOException ignored) {
        }
    }
}
