/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.tasks;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.protocol.HttpContext;
import org.jhuapl.edu.sages.sandbox.xformparser.SAXParseSMS;
import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.listeners.InstanceSMSerListener;
import org.odk.collect.android.preferences.PreferencesSmsActivity;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.utilities.SAXParserSMSUtil;
import org.odk.collect.android.utilities.WebUtils;

import src.sandbox.DataChunker;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.util.Log;
import android.webkit.MimeTypeMap;

/**
 * Background task for uploading completed forms.
 * 
 * @author Adjoa Poku (adjoa.poku@jhuapl.edu)
 */
public class InstanceSMSerTask extends AsyncTask<Long, Integer, HashMap<String, String>> {

    private static String t = "InstanceSMSerTask";
    private static final int MULTIPART_SMS_SIZE = 100;
    private InstanceSMSerListener mStateListener;
    private static final int CONNECTION_TIMEOUT = 30000;
    private static final String fail = "FAILED: ";

    private URI mAuthRequestingServer;
    HashMap<String, String> mResults;


    // TODO: This method is like 350 lines long, down from 400.
    // still. ridiculous. make it smaller.
    @Override
    protected HashMap<String, String> doInBackground(Long... values) {
        mResults = new HashMap<String, String>();

        String selection = InstanceColumns._ID + "=?";
        String[] selectionArgs = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            if (i != values.length - 1) {
                selection += " or " + InstanceColumns._ID + "=?";
            }
            selectionArgs[i] = values[i].toString();
        }

        // get shared HttpContext so that authentication and cookies are retained.
        HttpContext localContext = Collect.getInstance().getHttpContext();
        HttpClient httpclient = WebUtils.createHttpClient(CONNECTION_TIMEOUT);

        Map<URI, URI> uriRemap = new HashMap<URI, URI>();

        Cursor c =
            Collect.getInstance().getContentResolver()
                    .query(InstanceColumns.CONTENT_URI, null, selection, selectionArgs, null);

        if (c.getCount() > 0) {
            c.moveToPosition(-1);
            next_submission: while (c.moveToNext()) {
                if (isCancelled()) {
                    return mResults;
                }
                publishProgress(c.getPosition() + 1, c.getCount());
                String instance = c.getString(c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
                String id = c.getString(c.getColumnIndex(InstanceColumns._ID));
                Uri toUpdate = Uri.withAppendedPath(InstanceColumns.CONTENT_URI, id);

                String urlString = c.getString(c.getColumnIndex(InstanceColumns.SUBMISSION_URI));
                if (urlString == null) {
                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(Collect.getInstance());
                    urlString = settings.getString(PreferencesSmsActivity.KEY_GSMSERVER_NUM, null);
                    String submissionUrl = "sms://";
                    		//settings.getString(PreferencesSmsActivity.KEY_SUBMISSION_URL, "/submission");
                    urlString = submissionUrl + urlString;
                }

            	final String formIdColName = "jrFormId";
            	final int formIdColIndex = c.getColumnIndex("jrFormId");
            	final String formId = c.getString(formIdColIndex);
            	
                ContentValues cv = new ContentValues();
                URI u = null;
                String smsNumber = null;
// TODO POKU: we don't interact with HTTP server...need to do gsm modem metaphor?
                try {
                	if (SAXParserSMSUtil.isValidSMSURL(urlString)){
                		//look for SMS number defined in ODK Preferences...
                	}
                	
                	// parse smsnumber to receive the sms
                	smsNumber = SAXParserSMSUtil.parseSMSfromURL(urlString);
                	
                	//URL url = new URL(urlString);
                    //u = url.toURI();
                    
                } /*catch (MalformedURLException e) {
                    e.printStackTrace();
                    mResults.put(id,
                        fail + "invalid url: " + urlString + " :: details: " + e.getMessage());
                    cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                    Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                    continue;
                }*/ catch (URISyntaxException e) {
                    e.printStackTrace();
                    mResults.put(id,
                        fail + "invalid uri: " + urlString + " :: details: " + e.getMessage());
                    cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                    Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                    continue;
                }

//                boolean openRosaServer = false;
//                if (uriRemap.containsKey(u)) {
//                    // we already issued a head request and got a response,
//                    // so we know the proper URL to send the submission to
//                    // and the proper scheme. We also know that it was an
//                    // OpenRosa compliant server.
//                    openRosaServer = true;
//                    u = uriRemap.get(u);
//                } else {
//                    // we need to issue a head request
//                    HttpHead httpHead = WebUtils.createOpenRosaHttpHead(u);
//
//                    // prepare response
//                    HttpResponse response = null;
//                    try {
//                        response = httpclient.execute(httpHead, localContext);
//                        int statusCode = response.getStatusLine().getStatusCode();
//                        if (statusCode == 401) {
//                            // we need authentication, so stop and return what we've
//                            // done so far.
//                            mAuthRequestingServer = u;
//                        } else if (statusCode == 204) {
//                            Header[] locations = response.getHeaders("Location");
//                            if (locations != null && locations.length == 1) {
//                                try {
//                                    URL url = new URL(locations[0].getValue());
//                                    URI uNew = url.toURI();
//                                    if (u.getHost().equalsIgnoreCase(uNew.getHost())) {
//                                        openRosaServer = true;
//                                        // trust the server to tell us a new location
//                                        // ... and possibly to use https instead.
//                                        uriRemap.put(u, uNew);
//                                        u = uNew;
//                                    } else {
//                                        // Don't follow a redirection attempt to a different host.
//                                        // We can't tell if this is a spoof or not.
//                                        mResults.put(
//                                            id,
//                                            fail
//                                                    + "Unexpected redirection attempt to a different host: "
//                                                    + uNew.toString());
//                                        cv.put(InstanceColumns.STATUS,
//                                            InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
//                                        Collect.getInstance().getContentResolver()
//                                                .update(toUpdate, cv, null, null);
//                                        continue;
//                                    }
//                                } catch (Exception e) {
//                                    e.printStackTrace();
//                                    mResults.put(id, fail + urlString + " " + e.getMessage());
//                                    cv.put(InstanceColumns.STATUS,
//                                        InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
//                                    Collect.getInstance().getContentResolver()
//                                            .update(toUpdate, cv, null, null);
//                                    continue;
//                                }
//                            }
//                        } else {
//                            // may be a server that does not handle
//                            try {
//                                // have to read the stream in order to reuse the connection
//                                InputStream is = response.getEntity().getContent();
//                                // read to end of stream...
//                                final long count = 1024L;
//                                while (is.skip(count) == count)
//                                    ;
//                                is.close();
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                            }
//
//                            Log.w(t, "Status code on Head request: " + statusCode);
//                            if (statusCode >= 200 && statusCode <= 299) {
//                                mResults.put(id, fail + "network login? ");
//                                cv.put(InstanceColumns.STATUS,
//                                    InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
//                                Collect.getInstance().getContentResolver()
//                                        .update(toUpdate, cv, null, null);
//                                continue;
//                            }
//                        }
//                    } catch (ClientProtocolException e) {
//                        e.printStackTrace();
//                        mResults.put(id, fail + "client protocol exeption?");
//                        cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
//                        Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
//                        continue;
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                        mResults.put(id, fail + "generic excpetion.  great");
//                        cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
//                        Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
//                        continue;
//                    }
//                }

                // At this point, we may have updated the uri to use https.
                // This occurs only if the Location header keeps the host name
                // the same. If it specifies a different host name, we error
                // out.
                //
                // And we may have set authentication cookies in our
                // cookiestore (referenced by localContext) that will enable
                // authenticated publication to the server.
                //
                // get instance file
                File instanceFile = new File(instance);

                if (!instanceFile.exists()) {
                    mResults.put(id, fail + "instance XML file does not exist!");
                    cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                    Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                    continue;
                }

                
                // find all files in parent directory
                File[] allFiles = instanceFile.getParentFile().listFiles();

//TODO: POKU no attachments so comment this block                
                // add media files
                List<File> files = new ArrayList<File>();
                for (File f : allFiles) {
                    String fileName = f.getName();

                    int dotIndex = fileName.lastIndexOf(".");
                    String extension = "";
                    if (dotIndex != -1) {
                        extension = fileName.substring(dotIndex + 1);
                    }

                    if (fileName.startsWith(".")) {
                        // ignore invisible files
                        continue;
                    }
                    if (fileName.equals(instanceFile.getName())) {
                        continue; // the xml file has already been added
                    } else if (false /* TODO: POKU need gsmmodem equiv to: openRosaServer*/) {
                        files.add(f);
                    } /*else if (extension.equals("jpg")) { // legacy 0.9x
                        files.add(f);
                    } else if (extension.equals("3gpp")) { // legacy 0.9x
                        files.add(f);
                    } else if (extension.equals("3gp")) { // legacy 0.9x
                        files.add(f);
                    } else if (extension.equals("mp4")) { // legacy 0.9x
                        files.add(f);
                    }*/ else {
                        Log.w(t, "unrecognized file type " + f.getName());
                    }
                }

                boolean first = true;
                int j = 0;
                while (j < files.size() || first) {
                    first = false;

                    HttpPost httppost = WebUtils.createOpenRosaHttpPost(u);

                    MimeTypeMap m = MimeTypeMap.getSingleton();

                    long byteCount = 0L;

                    // mime post
//                    MultipartEntity entity = new MultipartEntity();

                    // add the submission file first...
//                    FileBody fb = new FileBody(instanceFile, "text/xml");
//                    entity.addPart("xml_submission_file", fb);
//                    Log.i(t, "added xml_submission_file: " + instanceFile.getName());
//                    byteCount += instanceFile.length();

                    Log.i(t, "parsing to SMS the xml_submission_file: " + instanceFile.getName());
                    String smsText = null;
                    try {
                    	SAXParserFactory factory = SAXParserFactory.newInstance();
                    	SAXParser saxParser = factory.newSAXParser();
                    	
                    	
                    	SharedPreferences settings =
                                PreferenceManager.getDefaultSharedPreferences(Collect.getInstance());
                    	String delimiter = settings.getString(PreferencesSmsActivity.KEY_DELIMITER, " ");
                    	smsNumber = settings.getString(PreferencesSmsActivity.KEY_GSMSERVER_NUM, smsNumber);
                    	String ticksymbol = settings.getString(PreferencesSmsActivity.KEY_TICKSYMBOL, null);
                    	
                    	boolean useTicks = settings.getBoolean(PreferencesSmsActivity.KEY_USE_TICKS, false);
                    	boolean preserveFormat = settings.getBoolean(PreferencesSmsActivity.KEY_PRESERVE_FORMAT, false);
                    	boolean useFieldTags = settings.getBoolean(PreferencesSmsActivity.KEY_INCLUDE_TAGS, false);
                    	boolean fillBlanks = settings.getBoolean(PreferencesSmsActivity.KEY_FILL_BLANKS, true);
                    	

                    	
                    	SAXParseSMS handler = new SAXParseSMS(delimiter, preserveFormat, useFieldTags, fillBlanks, useTicks);
                    	if (useTicks){
                    		handler.setTickSymbols(ticksymbol);
                    	}
                    	if ("multisms".equals(formId)){ //is this needed? handler sees the "id" element...
                    		handler.setIsMultiSms(true);
                    	}
                    	
                    	saxParser.parse(instanceFile, handler);
                    	
                    	Log.i(t, "SMS STRING: " + handler.getSMSstring());
                    	smsText = handler.getSMSstring().toString();
                    
                    } catch (Exception e){
                    	
                    }
                    
                    int blobLength = smsText.length();
                    ArrayList<String> dividedBlob = null;
                    
                    if (blobLength > 160){
                    	if ("multisms".equals(formId)){
                    		smsText.replaceFirst(formId, "");
                    	} else {
                    		// prepare for header: segNum,totSegs,txID:#formID
                    		smsText.replaceFirst(formId, "#" + formId);
                    	}
                    	try {
//                    	dividedBlob = divideText(smsText, 100);
                    	//MULTIPART_SMS_SIZE == 100;
                    	dividedBlob = divideTextAddHeader(smsText, MULTIPART_SMS_SIZE, formId);
                    	} catch (Exception e){
                    		e.printStackTrace();
                    		Log.e(t + ".divideTextAddHeader", e.getMessage());
                    	}
                    	Calendar cal = new GregorianCalendar();
                    	int dayOfYear = cal.get(Calendar.DAY_OF_YEAR);
                    	int hr = cal.get(Calendar.HOUR_OF_DAY);
                    	int min = cal.get(Calendar.MINUTE);
                    	int sec = cal.get(Calendar.SECOND);
                    	String txId = dayOfYear + "" + hr + "" + min + "" + sec;
                    	//applyHeaders(dividedBlob, txId);
                    }
                    // now onto sending the SMS
                    SmsManager smsmanger = SmsManager.getDefault();
                    if (dividedBlob !=  null){
                    	//smsmanger.sendTextMessage(smsNumber, null, "dummy test", null, null/*sentIntent, deliveryIntent*/);
                    	smsmanger.sendMultipartTextMessage(smsNumber, null, dividedBlob, null, null/* sentIntents, deliveryIntents*/);
                    } else
                    if (smsText.length() > 140) {
                    	ArrayList<String> dividedText = smsmanger.divideMessage(smsText);
                    	smsmanger.sendMultipartTextMessage(smsNumber, null, dividedText, null, null/* sentIntents, deliveryIntents*/);
                    } else {
                    	smsmanger.sendTextMessage(smsNumber, null, smsText, null, null/*sentIntent, deliveryIntent*/);
                    }
// TODO: POKU this skipped, think it's for attachments not sure but prolly dont need it either                    
//                    for (; j < files.size(); j++) {
//                        File f = files.get(j);
//                        String fileName = f.getName();
//                        int idx = fileName.lastIndexOf(".");
//                        String extension = "";
//                        if (idx != -1) {
//                            extension = fileName.substring(idx + 1);
//                        }
//                        String contentType = m.getMimeTypeFromExtension(extension);
//
//                        // we will be processing every one of these, so
//                        // we only need to deal with the content type determination...
//                        if (extension.equals("xml")) {
//                            fb = new FileBody(f, "text/xml");
//                            entity.addPart(f.getName(), fb);
//                            byteCount += f.length();
//                            Log.i(t, "added xml file " + f.getName());
//                        } 
/*
                         else if (extension.equals("jpg")) {
                            fb = new FileBody(f, "image/jpeg");
                            entity.addPart(f.getName(), fb);
                            byteCount += f.length();
                            Log.i(t, "added image file " + f.getName());
                        } else if (extension.equals("3gpp")) {
                            fb = new FileBody(f, "audio/3gpp");
                            entity.addPart(f.getName(), fb);
                            byteCount += f.length();
                            Log.i(t, "added audio file " + f.getName());
                        } else if (extension.equals("3gp")) {
                            fb = new FileBody(f, "video/3gpp");
                            entity.addPart(f.getName(), fb);
                            byteCount += f.length();
                            Log.i(t, "added video file " + f.getName());
                        } else if (extension.equals("mp4")) {
                            fb = new FileBody(f, "video/mp4");
                            entity.addPart(f.getName(), fb);
                            byteCount += f.length();
                            Log.i(t, "added video file " + f.getName());
                        } else if (extension.equals("csv")) {
                            fb = new FileBody(f, "text/csv");
                            entity.addPart(f.getName(), fb);
                            byteCount += f.length();
                            Log.i(t, "added csv file " + f.getName());
                        } else if (f.getName().endsWith(".amr")) {
                            fb = new FileBody(f, "audio/amr");
                            entity.addPart(f.getName(), fb);
                            Log.i(t, "added audio file " + f.getName());
                        } else if (extension.equals("xls")) {
                            fb = new FileBody(f, "application/vnd.ms-excel");
                            entity.addPart(f.getName(), fb);
                            byteCount += f.length();
                            Log.i(t, "added xls file " + f.getName());
                        } else if (contentType != null) {
                            fb = new FileBody(f, contentType);
                            entity.addPart(f.getName(), fb);
                            byteCount += f.length();
                            Log.i(t,
                                "added recognized filetype (" + contentType + ") " + f.getName());
                        } else {
                            contentType = "application/octet-stream";
                            fb = new FileBody(f, contentType);
                            entity.addPart(f.getName(), fb);
                            byteCount += f.length();
                            Log.w(t, "added unrecognized file (" + contentType + ") " + f.getName());
                        }
*/
                        

                        // we've added at least one attachment to the request...
//poku SMS won't do attchmnts
/*                        if (j + 1 < files.size()) {
                            if (byteCount + files.get(j + 1).length() > 10000000L) {
                                // the next file would exceed the 10MB threshold...
                                Log.i(t, "Extremely long post is being split into multiple posts");
                                try {
                                    StringBody sb = new StringBody("yes", Charset.forName("UTF-8"));
                                    entity.addPart("*isIncomplete*", sb);
                                } catch (Exception e) {
                                    e.printStackTrace(); // never happens...
                                }
                                ++j; // advance over the last attachment added...
                                break;
                            }
                        }*/
                        
//TODO: POKU matches "for (; j < files.size(); j++) {" ->  }
                    
/*
                    httppost.setEntity(entity);

                    // prepare response and return uploaded
                    HttpResponse response = null;
                    try {
                        response = httpclient.execute(httppost, localContext);
                        int responseCode = response.getStatusLine().getStatusCode();

                        try {
                            // have to read the stream in order to reuse the connection
                            InputStream is = response.getEntity().getContent();
                            // read to end of stream...
                            final long count = 1024L;
                            while (is.skip(count) == count)
                                ;
                            is.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Log.i(t, "Response code:" + responseCode);
                        // verify that the response was a 201 or 202.
                        // If it wasn't, the submission has failed.
                        if (responseCode != 201 && responseCode != 202) {
                            if (responseCode == 200) {
                                mResults.put(id, fail + "Network login failure?  again?");
                            } else {
                                mResults.put(id, fail + urlString + " returned " + responseCode + " " + 
                                        response.getStatusLine().getReasonPhrase());
                            }
                            cv.put(InstanceColumns.STATUS,
                                InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                            Collect.getInstance().getContentResolver()
                                    .update(toUpdate, cv, null, null);
                            continue next_submission;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        mResults.put(id, fail + "generic exception... " + e.getMessage());
                        cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                        Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                        continue next_submission;
                    }
*/
                }

                
                // if it got here, it must have worked
                mResults.put(id, Collect.getInstance().getString(R.string.success));
                cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMITTED);
                Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);

            }
            if (c != null) {
                c.close();
            }

        } // end while

        return mResults;
    }


    /**
	 * @param dividedBlob
	 * @param txId
	 */
	private void applyHeaders(List<String> dividedBlob, String txId) {
		int totalSegs = dividedBlob.size();
		for (int i = 1; i <= dividedBlob.size() + 1; i++){
			String segText = dividedBlob.get(i);
			String header = i + "," + totalSegs + "," + txId + ":";
			segText = header + segText;
			dividedBlob.set(i, segText);
		}
		System.out.println(txId);
	}


	/**
	 * @param smsText
	 * @param i
	 */
	private ArrayList<String> divideText(String smsText, int segSize) {
		List<String> dividedText = new ArrayList<String>();
		int numSegs = (int) Math.round(smsText.length() / (double) segSize);
		int start = 0;
		int end = segSize -1;
		
		String tmpString = "";
//		String[] s = StringUtils.splitPreserveAllTokens(smsText, null, numSegs);
		String[] s = DataChunker.chunkData(smsText);
//		while (tmpString >= segSize){
//			tmpString = smsText.substring(0,segSize - 1);
//				
//		}
/*		for (int i=0; i <= numSegs; i++){
			
			dividedText.add(smsText.substring(start, end));
			start = end + 1;
			end = end + segSize;
		}*/
		
		dividedText = Arrays.asList(s);
		return new ArrayList<String>(dividedText);
	}
	
	/**
	 * @param smsText
	 * @param i
	 */
	private ArrayList<String> divideTextAddHeader(String smsText, int segSize, String formId) {
		ArrayList<String> dividedText = new ArrayList<String>();
		int numSegs = (int) Math.round(smsText.length() / (double) segSize);
		int start = 0;
		int end = segSize -1;
		
		String tmpString = "";
//		String[] s = StringUtils.splitPreserveAllTokens(smsText, null, numSegs);
//TODO		String[] s = DataChunker.chunkData(smsText);
		Map<String,String> s = DataChunker.chunkDataWithHeader(smsText, formId);
//		while (tmpString >= segSize){
//			tmpString = smsText.substring(0,segSize - 1);
//				
//		}
		/*		for (int i=0; i <= numSegs; i++){
			
			dividedText.add(smsText.substring(start, end));
			start = end + 1;
			end = end + segSize;
		}*/
		
		for (Entry<String,String> entry : s.entrySet()){
			dividedText.add(entry.getKey() + entry.getValue());
		}
//		dividedText = Arrays.asList(s);
		return dividedText;
	}


	@Override
    protected void onPostExecute(HashMap<String, String> value) {
        synchronized (this) {
            if (mStateListener != null) {
                if (mAuthRequestingServer != null) {
                    mStateListener.authRequest(mAuthRequestingServer, mResults);
                } else {
                    mStateListener.uploadingComplete(value);
                }
            }
        }
    }


    @Override
    protected void onProgressUpdate(Integer... values) {
        synchronized (this) {
            if (mStateListener != null) {
                // update progress and total
                mStateListener.progressUpdate(values[0].intValue(), values[1].intValue());
            }
        }
    }


    public void setSMSerListener(InstanceSMSerListener sl) {
        synchronized (this) {
            mStateListener = sl;
        }
    }
}
