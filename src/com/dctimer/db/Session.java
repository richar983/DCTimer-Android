package com.dctimer.db;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.dctimer.Configs;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

public class Session {
	private Cursor cursor;
	private DBHelper dbh;
	public int dbLastId;
	public static int[] result;
	public static int resl;
	public static byte[] penalty;
	public static int[][] mulp = null;
	public static long[] multemp = new long[7];
	private boolean mark = false;
	private int crntSes = 0;
	
	private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
	
	public Session(Context c) {
		dbh = new DBHelper(c);
	}
	
	public void closeDB() {
		if(cursor != null)
			cursor.close();
		dbh.close();
	}
	
	public Cursor getCursor(int sesIdx) {
		return dbh.query(sesIdx);
	}
	
	public void getSession(int i, boolean isMulp) {
		crntSes = i;
		cursor = dbh.query(i);
		resl = cursor.getCount();
		if(resl == 0) {
			result = new int[24];
			penalty = new byte[24];
			if(isMulp) mulp = new int[6][24];
			dbLastId = 0;
		} else {
			if(resl < 500) {
				result = new int[resl*2];
				penalty = new byte[resl*2];
				if(isMulp) mulp = new int[6][resl*2];
			} else {
				result = new int[resl*3/2];
				penalty = new byte[resl*3/2];
				if(isMulp) mulp = new int[6][resl*3/2];
			}
			cursor.moveToFirst();
			for(int k=0; k<resl; k++) {
				result[k] = cursor.getInt(1);
				penalty[k] = (byte) cursor.getInt(2);
				if(cursor.getInt(3) == 0) penalty[k]=2;
				if(isMulp)
					for(int j=0; j<6; j++)
						mulp[j][k] = cursor.getInt(7+j);
				cursor.moveToNext();
			}
			cursor.moveToLast();
			dbLastId = cursor.getInt(0);
		}
	}
	
	public void move(int pos) {
		if(mark) {
			cursor = dbh.query(crntSes);
			mark = false;
		}
		cursor.moveToPosition(pos);
	}
	
	public int getInt(int i) {
		return cursor.getInt(i);
	}
	
	public String getString(int i) {
		return cursor.getString(i);
	}
	
	public String getString(int pos, int i) {
		if(mark) {
			cursor = dbh.query(crntSes);
			mark = false;
		}
		cursor.moveToPosition(pos);
		return cursor.getString(i);
	}
	
	public static int getTime(int i) {
		return result[i] + penalty[i] * 2000;
	}
	
	public void insert(int time, int p, String scr, boolean isMulp) {
		if(resl >= result.length) {
			expand(isMulp);
		}
		penalty[resl] = (byte) p;
		result[resl++]=time;
		if(isMulp) {
			boolean temp = true;
			for(int i=0; i<Configs.stSel[3]+1; i++) {
				if(temp)
					mulp[i][resl-1] = (int)(multemp[i+1]-multemp[i]);
				else mulp[i][resl-1] = 0;
				if(mulp[i][resl-1]<0 || mulp[i][resl-1]>result[resl-1]) {
					mulp[i][resl-1]=0; temp=false;
				}
			}
		}
		int d = 1;
		if(p==2) p=d=0;
		ContentValues cv = new ContentValues();
		cv.put("id", ++dbLastId);
		cv.put("rest", time);
		cv.put("resp", p);
		cv.put("resd", d);
		cv.put("scr", scr);
		cv.put("time", formatter.format(new Date()));
		if(isMulp)
			for(int i=0; i<6; i++)
				cv.put("p"+(i+1), mulp[i][resl-1]);
		dbh.insert(crntSes, cv);
		mark = true;
	}
	
	public void expand(boolean isMulp) {
		byte[] rep2;
		int[] res2;
		if(resl < 1000) {
			rep2 = new byte[penalty.length*2];
			res2 = new int[result.length*2];
		} else {
			rep2 = new byte[penalty.length*3/2];
			res2 = new int[result.length*3/2];
		}
		for(int i=0; i<resl; i++) {
			rep2[i] = penalty[i];
			res2[i] = result[i];
		}
		penalty = rep2;
		Session.result = res2;
		if(isMulp) {
			int[][] mulp2 = new int[6][result.length];
			for(int i=0; i<resl; i++)
				for(int j=0; j<6; j++)
					mulp2[j][i] = mulp[j][i];
			mulp = mulp2;
		}
	}
	
	public void update(int idx, byte p) {
		penalty[idx] = p;
		byte d = 1;
		if(p==2) p=d=0;
		cursor.moveToPosition(idx);
		int id = cursor.getInt(0);
		dbh.update(crntSes, id, p, d);
		mark = true;
	}
	
	public void update(String text) {
		int id = cursor.getInt(0);
		dbh.update(crntSes, id, text);
		mark = true;
	}
	
	public void delete(int idx, boolean isMulp) {
		int delId;
		if(idx != resl-1) {
			for(int i=idx; i<resl-1; i++) {
				result[i]=result[i+1]; penalty[i]=penalty[i+1];
				if(isMulp)
					for(int j=0; j<Configs.stSel[3]+1; j++)
						mulp[j][i] = mulp[j][i+1];
			}
			cursor.moveToPosition(idx);
			delId = cursor.getInt(0);
		} else {
			delId = dbLastId;
			if(resl > 1) {
				cursor.moveToPosition(resl-2);
				dbLastId = cursor.getInt(0);
			} else dbLastId = 0;
		}
		dbh.del(crntSes, delId);
		resl--;
		mark = true;
	}
	
	public void clear() {
		dbh.clear(crntSes);
		resl = dbLastId = 0;
		mark = true;
	}
	
	public void getMultData() {
		cursor = dbh.query(crntSes);
		for(int i=0; i<resl; i++) {
			cursor.moveToPosition(i);
			for(int j=0; j<6; j++)
				mulp[j][i] = cursor.getInt(7+j);
		}
	}
}
