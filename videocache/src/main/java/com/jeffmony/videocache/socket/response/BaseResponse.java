package com.jeffmony.videocache.socket.response;

import android.text.TextUtils;

import com.jeffmony.videocache.common.VideoCacheConfig;
import com.jeffmony.videocache.common.VideoCacheException;
import com.jeffmony.videocache.socket.request.ChunkedOutputStream;
import com.jeffmony.videocache.socket.request.ContentType;
import com.jeffmony.videocache.socket.request.HttpRequest;
import com.jeffmony.videocache.socket.request.IState;
import com.jeffmony.videocache.socket.request.Method;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public abstract class BaseResponse {
    private static final String TAG = "BaseResponse";
    protected static String CONTENT_TYPE = "Content-Type";
    protected static String DATE = "Date";
    protected static String CONNECTION = "Connection";
    protected static String TRANSFER_ENCODING = "Transfer-Encoding";
    protected static String GMT_PATTERN = "E, d MMM yyyy HH:mm:ss 'GMT'";
    protected static final int WAIT_TIME = 50;
    protected static final int MAX_WAIT_TIME = 2 * 1000;

    protected final HttpRequest mRequest;
    protected final VideoCacheConfig mConfig;
    protected final String mCachePath;
    protected final String mVideoUrl;
    protected Map<String, String> mHeaders;
    protected final String mMimeType;
    protected final String mProtocolVersion;
    protected IState mResponseState;

    public BaseResponse(HttpRequest request, VideoCacheConfig config, String videoUrl, Map<String, String> headers) {
        mRequest = request;
        mConfig = config;
        mCachePath = config.getFilePath();
        mVideoUrl = videoUrl;
        mHeaders = headers;
        mMimeType = request.getMimeType();
        mProtocolVersion = request.getProtocolVersion();
    }

    public void sendResponse(Socket socket, OutputStream outputStream) throws VideoCacheException {
        SimpleDateFormat gmtFormat = new SimpleDateFormat(GMT_PATTERN, Locale.US);
        gmtFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        try {
            if (mResponseState == null) {
                throw new VideoCacheException("sendResponse(): Status can't be null.");
            }
            PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                    outputStream, new ContentType(mMimeType).getEncoding())),false);
            if (TextUtils.isEmpty(mProtocolVersion)) {
                pw.append("HTTP/1.1 ");
            } else {
                pw.append(mProtocolVersion + " ");
            }
            pw.append(mResponseState.getDescription()).append(" \r\n");
            if (!TextUtils.isEmpty(mMimeType)) {
                appendHeader(pw, CONTENT_TYPE, mMimeType);
            }
            appendHeader(pw, DATE, gmtFormat.format(new Date()));
            appendHeader(pw, CONNECTION, (mRequest.keepAlive() ? "keep-alive" : "close"));
            if (mRequest.requestMethod() != Method.HEAD) {
                appendHeader(pw, TRANSFER_ENCODING, "chunked");
            }
            pw.append("\r\n");
            pw.flush();
            sendBodyWithCorrectTransferAndEncoding(socket, outputStream);
            outputStream.flush();
        } catch (Exception e) {
            throw new VideoCacheException("send response failed: ", e);
        }
    }

    protected void appendHeader(PrintWriter pw, String key, String value) {
        pw.append(key).append(": ").append(value).append("\r\n");
    }

    protected void sendBodyWithCorrectTransferAndEncoding(Socket socket, OutputStream outputStream) throws Exception {
        ChunkedOutputStream chunkedOutputStream = new ChunkedOutputStream(outputStream);
        sendBody(socket, chunkedOutputStream, -1);
        chunkedOutputStream.finish();
    }

    public abstract void sendBody(Socket socket, OutputStream outputStream, long pending) throws Exception;
}
