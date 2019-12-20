import java.io.*;
import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.gui.OvalRoi;
import ij.plugin.*;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.gui.GenericDialog;
import ij.gui.DialogListener;
import ij.process.*;
import java.util.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.util.Vector;
import java.io.PrintWriter;
import java.lang.Float;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;  
import java.time.LocalDateTime;  
import java.util.concurrent.atomic.AtomicInteger;
 
/** Draw_Vectors_Atlantic
  *
  * Plots wind vectors overlaid on background image
  *
  */

public class MultiLevel_Vectors_Atlantic implements ExtendedPlugInFilter, DialogListener, ActionListener {

   ImagePlus img;
   GeneralPath path;
   Overlay overlay;
   long RunStartTime;
   long RunEndTime;

   boolean DebugTrajectories = false;
   boolean DebugBalloons = false;
   boolean DebugConnectivity = false;
   boolean LogOrange = false;
   boolean LogBlue = false;
   boolean LogBlack = false;
   boolean LogYellow = false;
   
   int BalloonLaunchTime = 0; //1773; //6738; //6420;
   int UsePrevLaunchGap = 12; //12;
   // if no defaults defined in ImageJ/P2PHAPSdefaults.txt (or in WindsConfig*.txt) then the following will be used...
   String TracksDir = "D:\\CGI\\P2PHAPS\\Tracks";
   String PluginsDir = "D:\\ImageJ\\plugins";
   String ConfigDir = "D:\\ImageJ\\plugins";
   String DataDir = "D:\\CGI\\P2PHAPS\\ImageJ";
   
   String FileUfname = "D:\\CGI\\P2PHAPS\\WindData\\winds_u.r4";                 // otherwise assumed to be in DataDir
   String FileVfname = "D:\\CGI\\P2PHAPS\\WindData\\winds_v.r4";                 // otherwise assumed to be in DataDir
   String WindFilesList = ""; //"D:\\CGI\\P2PHAPS\\WindFilesList.txt";           // otherwise assumed to be in DataDir
   
   String SitesDir = "D:\\CGI\\P2PHAPS\\ImageJ";                                 // otherwise assumed to be in ConfigDir
   String GeodesicFname = "D:\\CGI\\P2PHAPS\\WindData\\Geodesic.csv";            // otherwise assumed to be in ConfigDir
   String LaunchSitesFname = "D:\\CGI\\P2PHAPS\\WindData\\LaunchSites.txt";      // otherwise assumed to be in ConfigDir
   String LandingSitesFname = "D:\\CGI\\P2PHAPS\\ImageJ\\LandingSites.txt";      // otherwise assumed to be in ConfigDir
   String GroundStationsFname = "D:\\CGI\\P2PHAPS\\ImageJ\\GroundStations.txt";  // otherwise assumed to be in ConfigDir
   String BackgroundImage = "D:\\CGI\\P2PHAPS\\ImageJ\\LatLonNewNA.png";         // otherwise assumed to be in ConfigDir
   
   String MultiTrackFname = "D:\\CGI\\P2PHAPS\\MultiTracks.txt";                 // otherwise assumed to be in TracksDir
   
   double RadToDeg = 180.0/3.1415926;     // Conversion from radians to degrees
   double DegToRad = 1.0/RadToDeg;        // Conversion from degrees to radians 
   double EarthSize = 40075000.0;         // metres for 360 degs at equator
   double EarthRadius = 6378.13;          // kilometres radius (based on equatorial circi=umference
   float  MinVisibilityHeight = (float) 15.8;     // height used to determine min acceptable visibility
   double size = 4.0; // size of circle for showing balloons

   boolean DoExtrapolateHeights = false; //true;
   int MetNumX = 144; // Number of longitude values (=144 for 2.5 degree data, 0.0 to 377.5 = 360.0-2.5)
   int MetNumY = 73; // Number of latitude values (=73 for 2.5 degree data, +90.0 to -90.0))
   int MetNumH = 17; // Number of height values (typically=17 pressure levels)
   int MetNumT = 365; // Number of image times (eg 365 for one-per-day, 4x365 for 6-hourly data)
   double MetCellSize = 2.5; // degrees spatial resolution
   double MetNumImages = 0; // Calculated as equivalent to MetNumT*MetNumH
   double MetTimeStep = 24.0; // time between met data samples (hours)
   int MetIntervalMultiplier = 1; // Used for simulating more separated met data intervals (eg if MetTimeStep is 3 hours, and MetIntervalMultiplier=8, then simulates daily met data updates
   int MaxBalloonTimeSteps = 0;
   float StartBalloonLaunchTime = 0;
   int DialogXpos = -1;
   int DialogYpos = -1;

   double minlat = 30.0;
   double minlon = -105.0;
   double maxlat = 90.0;
   double maxlon = 15.0;
   
   int FirstTime = 1;
   int GridArrowMode = 1;
   int PlotGrid = 1;
   double arrowScale = 4.0;
   double headScale = 0.25;
   int iheight=11;
   int previheight=11;
   int previheight1=11;
   int imTimeStep=0;
   int imonth = 0;
   int iday = 0;
   int ihour = 0;
   int iStartDoY = 0;
   int iQuarterDay = 0;
   double Dlat = 0.0;
   double AbsDlat = 0.0;
   double newEndLat = 0.0;
   double newEndLon = 0.0;
    
   boolean ShowGeodesic = true;
   boolean EastToWest = false;
   static double DeltaX = 0.0;
   static double DeltaY = 0.0;
   int LLgrid = 10; // lat/lon grid spacing (=0 for none)
   double StartLat = 53.71; // Launch Lat
   double StartLon = -57.0; // Launch Lon
   
   double TargetEndLon = -5.85; // Longitude of centre of Landing Zone
   double TargetEndLat = 43.6; // Latitude of centre of Landing Zone

   int MaxGroundStations = 10;
   int NumGroundStations = 0; //3;
   String[] GroundStationName = new String[MaxGroundStations]; 
   double[] GroundStationLat = new double[MaxGroundStations]; 
   double[] GroundStationLon = new double[MaxGroundStations]; 

   int MaxLaunchSites = 20;
   int NumLaunchSites = 0; //3;
   int SelectedLaunchSite = 8; //Sept-Iles
   String[] LaunchName = new String[MaxLaunchSites]; //{"Cartwright", "Sonora", "CapeCod" };
   double[] LaunchLat = new double[MaxLaunchSites];  //{ 53.71,    45.11,   42.1 };
   double[] LaunchLon = new double[MaxLaunchSites];  //{ -57.0,   -61.93,  -70.15 };
   String[] LaunchFiles = new String[MaxLaunchSites]; 
   String[] LaunchChoices = new String[MaxLaunchSites]; 
   String[] Choices = new String[MaxLaunchSites]; 
  
   int MaxLandingSites = 20;
   int NumLandingSites = 0; //3;
   int SelectedLandingSite = 1;
   String[] LandingName = new String[MaxLandingSites]; 
   double[] LandingLat = new double[MaxLandingSites]; 
   double[] LandingLon = new double[MaxLandingSites];  
   String[] LandingFiles = new String[MaxLandingSites]; 
   String[] LandingChoices = new String[MaxLandingSites]; 
  
   double[] EndLat = new double[MaxLaunchSites];  // Latitude at which TargetEndLon is reached (if at all)
   double[] EndLon = new double[MaxLaunchSites];  // Latitude at which TargetEndLon is reached (if at all)
   double[] EndTime = new double[MaxLaunchSites];  // Time (since start, in hours) at which TargetEndLon is reached (if at all)
   double[] fracHeight = new double[MaxLaunchSites];   // fractional per-launch-site height level for optimum single-height trajectory
   double HeightFraction = 0.0;
   String ConfigOpt = "Default";
   String PrevConfigOpt = "Default";
   double TimeStep = 3.0; // hours
   double IntegTimeStep = 3.0; // hours
   double SplittingInterval = 24.0; // Hours
   int MaxSplitIntervals = 3;
   double MaxTimeHours = 300.0; // 240.0; // Maximum time for running trajectories
   double[] IntervalHeight = new double[(int)MaxTimeHours];
   double prevLat[] = new double[(int)MaxTimeHours];
   int MaxTimeSteps = 80; // = 240/3.0 = MaxTimeHours/TimeStep
   int InterpMode = 1; // linear or quadratic?
   int StartHeight = 11; // 15.8 kms for 2.5 deg data, =4 for 1.0 deg data
   int EndHeight = 17; // 25.9 kms for 2.5 deg data, =11 for 15.8kms for 1 deg data, =15 for 31kms for 1deg data
   int MetSubStep = 0;
   int NumMetSubSteps = 1; // interpolate NumMetSubSteps between each Met Data set (eg if daily met data and NumMetSubSteps=24, then hourly met data used)
   double DeltaMetSubStep = 0.0;
   int MaxSplitHalfRange = 5;
   int LaunchPeriodStart = 0;  // Start of "Yearly" runs in units of MetData/NumMetSubSteps (eg Daily Met with NumMetSubSteps=4 => units = 6 hrs) [= user-defined imTimeStep]
   int LaunchPeriodDuration = 365; // Duration of Launch "Yearly" runs, in days [user-specified]
   int LaunchPeriodEnd = 0; // End of "Yearly" runs in units of MetData/NumMetSubSteps (eg Daily Met with NumMetSubSteps=4 => units = 6 hrs) [calc from Start & Duration]
   double dRangeSq = 0;
   int DialogMaxBalloonTimeSteps = 0;
   
   int ConfigMetNumX = 0;
   int ConfigMetNumY = 0;
   int ConfigMetTimeStep = 0;
   float ConfigMetCellSize = 0;
   
   String MetTime = "TBD";
   boolean TestGrib2 = false;
   boolean PlotBalloonsPos = false;
   boolean VisibilityDisplay = false;
   boolean ConnectivityDisplay = false;
   boolean AlongTrackWinds = false;
   boolean ShowGrid = false;
   boolean ShowTracks = false;
   boolean CalcBestTracks = false;
   boolean YearlyBestTracks = false;
   boolean MultiLevelBestTracks = false;
   boolean OutputAnyTrackDetails = false;
   boolean OutputBestTrackDetails = false;
   boolean PlotOnlyGoodTracks = true;
   boolean PlotNoHopers = false;
   boolean ShowMultiTracks = false;
   boolean UseTimeVarying = true;
   boolean ShowSites = false;
   boolean ConnectivityStats = false;
   int ConnectivityLevel=3;
   int IterConnectLevel=3;
   int[] LonBinCount = new int[32]; // currently hardwired for 8x 10-deg lon bins and 4 priorty/colour levels
 
   final AtomicInteger progress = new AtomicInteger(0);

   double BestHeight = 0.0;
   double BestEndTime = 0.0;
   double DlatAtMinDlat = 0.0;
   double TimeAtMinDlat = 0.0;
   double DlatlonThresh = 0.5;
   int BestLevel = -1;   	
   int countValidTracks = 0;
   int countYellowTracks = 0;
   int countBlackTracks = 0;
   int countBlueTracks = 0;
   int countCyanTracks = 0;
   int countOrangeTracks = 0;
   int CurrNumSplitIntervals = 0;
   static int NewRunNum = 0;
   static String CurrentDate;
   String NewRunName;
   int MaxLaunchTimes = 1;
   double MinSumSq = 0.0;
   int ConnectCount = 0;
   
   int inxsize;
   int inysize;
   
   double rlat,rlon;

   double rx,ry;
   double xwidth,yheight;
   float[] allFloatsU = {0};
   float[] allFloatsV = {0};
   float[] geolat = new float[1024];
   float[] geolon = new float[1024];
   int NumGeodesic = 0;
   double GeodesicSumSq = 0.0;
   
	double u = 0;
	double v = 0;
	double u2 = 0;
	double v2 = 0;
	
   int MaxMultiTrackFiles = 100;
   int NumMultiTrackFiles = 0;
   int SelectedMultiTrackFile = 0;
   String[] MultiTrackChoices = new String[MaxMultiTrackFiles];
   String[] MultiTrackFiles = new String[MaxMultiTrackFiles];
   
   String[] Month  = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
   String[] QtrHour = {"00:00","06:00","12:00","18:00"};
   
   int[] MonthDays = {31,28,31,30,31,30,31,31,30,31,30,31};
   int[] CumulDoY = {0,31,59,90,120,151,181,212,243,273,304,334,365};
   
   int[] HeightPa = {1000,925,850,700,600,500,400,300,250,200,150,100,70,50,30,20,10,5};
   double[] HeightKm = {0.0,0.8,1.5,3.0,4.2,5.6,7.2,9.1,10.4,11.8,13.5,15.8,17.7,19.3,21.6,23.3,25.9,27.0};
   
   double[] HeightGrib2Km = {9.1,10.4,11.8,13.5,15.8,17.7,19.3,21.6,23.3,24.3,26.0,27.0,28.0,29.0,30.0,31.0,32.0}; 
  
   long fileSizeU = 0;
   long fileSizeV = 0;
   long fileSizeFloat = 0;
   
   static int MaxBestTracks = 5000;
   int NumBestGeod = 0;
   double[] BestGeodSumSq = new double[MaxBestTracks];
   double[] BestGeodFracLat = new double[MaxBestTracks];
   int[] BestGeodNumIntervals = new int[MaxBestTracks];
   int[] BestGeodHt0 = new int[MaxBestTracks];
   int[] BestGeodHt1 = new int[MaxBestTracks];
   int[] BestGeodHt2 = new int[MaxBestTracks];
   int[] BestGeodHt3 = new int[MaxBestTracks];
   int[] BestGeodHt4 = new int[MaxBestTracks];
   int[] BestGeodHt5 = new int[MaxBestTracks];
   
   static int MaxTrackHours = 500; // 5 days is 120 hours
   static int MaxBalloonLaunchTimes = 9000; // every 6 hours for a year is 1460, every 3 hours is 2920
   boolean LoadedBestTracks = false;
   float[] BalloonLat = new float[MaxTrackHours*MaxBalloonLaunchTimes];
   float[] BalloonLon = new float[MaxTrackHours*MaxBalloonLaunchTimes];
   float[] BalloonHt = new float[MaxTrackHours*MaxBalloonLaunchTimes];
   float[] BalloonLaunchTimes = new float[MaxBalloonLaunchTimes];
 
   static int MaxConnections = 200; 
   int ConnectInd = 0;
   float[] ConnectListLat = new float[MaxConnections];
   float[] ConnectListLon = new float[MaxConnections];
   float[] ConnectListHt = new float[MaxConnections];
   float[] ConnectListLaunchTime = new float[MaxConnections];
   float[] ConnectListLength = new float[MaxConnections];
   int NumBad = 0;
   int NumGood = 0;
   int prevConnectInd = 0;
   float[] prevConnectListLat = new float[MaxConnections];
   float[] prevConnectListLon = new float[MaxConnections];
   float[] prevConnectListHt = new float[MaxConnections];
   float[] prevConnectListLaunchTime = new float[MaxConnections];
   float[] prevConnectListLength = new float[MaxConnections];

   int NumConnectedPath = 0;
   int ConnectedPathTime = 0;
   float[] ConnectedPathLat = new float[MaxConnections];
   float[] ConnectedPathLon = new float[MaxConnections];
   float[] ConnectedPathHt = new float[MaxConnections];
   float[] ConnectedPathLaunchTime = new float[MaxConnections];

   int NumNewTracks = 0;
   int NumGoodNewTracks = 0;
   int GribOffset = 0;
   double xprev = 0;
   double yprev = 0;
   
   String strStartLaunchDate = "20191009";
   String strStartLaunchTime = "0000";   
   PrintWriter writer;
   
   private final int flags = DOES_ALL|CONVERT_TO_FLOAT|FINAL_PROCESSING|PARALLELIZE_STACKS;

   public int setup(String arg, ImagePlus imp) {
		return DOES_ALL+DOES_STACKS+SUPPORTS_MASKING+NO_CHANGES;
   }
	/** This method is called by ImageJ to set the number of calls to run(ip)
	 *  corresponding to 100% of the progress bar */
	public void setNPasses (int nPasses) {
       // dummy function, needed by ImageJ
	}


   public static void ListDirs(String topdir) {
      try { 
      	 int MaxRunNum = 0;
      	 File dir = new File(topdir);
         File[] files = dir.listFiles();
         for (File file : files) {
            if (file.isDirectory()) {
            	String fname = file.getCanonicalPath();
            	int len = fname.length();
            	int inum = len-1;
            	for (inum=len-1;(inum>0) && (Character.isDigit(fname.charAt(inum))) ;inum--)
            	{
//                     IJ.log(fname+":"+inum+":"+fname.charAt(inum));
            	}

            	if (inum<len-1)
            	{
            	   String strRunNum =fname.substring(inum+1);
            	   int iRunNum = Integer.valueOf(strRunNum);
//                   System.out.println("directory: " + fname + " : " + strRunNum + " : " + iRunNum);
                   if (iRunNum>MaxRunNum)
                   	   MaxRunNum = iRunNum;
                }
            } 
         }
         NewRunNum = MaxRunNum+1;
      } catch (IOException e) {
         e.printStackTrace();
      } 
      DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd");  
      LocalDateTime now = LocalDateTime.now();       
      CurrentDate = dtf.format(now);
      //IJ.log("Current Date = "+CurrentDate+", NewRunNum="+NewRunNum);
   } 

   public  void ListFiles(String topdir, String wildcard) 
   {
   	   NumMultiTrackFiles = 0;
       try { 
      	 File dir = new File(topdir);
         File[] dirs = dir.listFiles();
         for (File subdir : dirs) 
         {
            if (subdir.isDirectory()) 
            {
                File[] files = subdir.listFiles();
                for (File file : files) 
                {
					if (!file.isDirectory()) 
					{
						String fname = file.getCanonicalPath();
						String basename = file.getName();
						int topdirlen = topdir.length();
						int len = fname.length();
						if ( (fname.contains(wildcard)) || ( (fname.contains("MultiTracks")) && (fname.contains(".txt"))) )
						{
							MultiTrackFiles[NumMultiTrackFiles] = fname;
							MultiTrackChoices[NumMultiTrackFiles] = fname.substring(topdirlen+1,fname.indexOf(basename)-1);
							//IJ.log("Num="+NumMultiTrackFiles+", : File="+fname+", choice="+MultiTrackChoices[NumMultiTrackFiles]);
							NumMultiTrackFiles++;
						}
					}
				}
            } 
         }
         MultiTrackChoices = Arrays.copyOf(MultiTrackChoices, NumMultiTrackFiles);
      } catch (IOException e) {
         e.printStackTrace();
      } 
    } 

   public  void ListSites(String topdir, String wildcard) 
   {
   	   int NumFiles = 0;
   	//IJ.log("ListSites: topdir="+topdir);   
       try { 
      	    File dir = new File(topdir);
            File[] files = dir.listFiles();

			for (File file : files) 
			{
				if (!file.isDirectory()) 
				{
					String fname = file.getCanonicalPath();
					String basename = file.getName();
					int topdirlen = topdir.length();
					int len = fname.length();
					//IJ.log("fname="+fname);
					if (fname.contains(wildcard))
					{
						if (wildcard.contains("Launch"))
							LaunchFiles[NumFiles] = fname;
						else
							LandingFiles[NumFiles] = fname;
							
						Choices[NumFiles] = basename.substring(wildcard.length(),basename.length()-4);
						//IJ.log("Num="+NumFiles+", : File="+fname+", choice="+Choices[NumFiles]);
						NumFiles++;
					}
				}
			}				
            if (wildcard.contains("Launch"))
               LaunchChoices = Arrays.copyOf(Choices, NumFiles);
            else
        	   LandingChoices = Arrays.copyOf(Choices, NumFiles);           
         }  catch (IOException e) {
         e.printStackTrace();
        } 
    } 

	int MonthFromDoY(int iDoY)
	{
		int i;
		int imonth = 0;
		for (i=0;i<12;i++)
		{
			if (iDoY>=CumulDoY[i])
				imonth = i;
		}
		return imonth;
	}
	
	void ReadDefaults()
	{
    	int iline = 0;
    	String row;
    	String fname="P2PHAPSdefaults.txt";
    	int NoWinds = 0;
    	
    	try(
    	    BufferedReader csvReader = new BufferedReader(new FileReader(fname));
    	)
    	{
			while ((row = csvReader.readLine()) != null) 
			{
				String[] data = row.split(" ");
				// do something with the data...
				//IJ.log("line="+iline+" :" + row + "data[0]="+data[0]+", data[1]="+data[1]);	
				if (data[0].compareTo("TracksDir")==0)
					TracksDir = data[1];
				else if (data[0].compareTo("SitesDir")==0)
					SitesDir = data[1];
				else if (data[0].compareTo("PluginsDir")==0)
					PluginsDir = data[1];
				else if (data[0].compareTo("ConfigDir")==0)
					ConfigDir = data[1];
				else if (data[0].compareTo("DataDir")==0)
					DataDir = data[1];
				
				else if (data[0].compareTo("BackgroundImage")==0)
					BackgroundImage = data[1];
				else if (data[0].compareTo("LaunchSitesFname")==0)
					LaunchSitesFname = data[1];
				else if (data[0].compareTo("LandingSitesFname")==0)
					LandingSitesFname = data[1];
				else if (data[0].compareTo("GroundStationsFname")==0)
					GroundStationsFname = data[1];
				else if (data[0].compareTo("GeodesicFname")==0)
					GeodesicFname = data[1];
				else if (data[0].compareTo("DialogXpos")==0)
					DialogXpos = Integer.parseInt(data[1]);
				else if (data[0].compareTo("DialogYpos")==0)
					DialogYpos = Integer.parseInt(data[1]);
				else
					IJ.log("Unknown Default Config setting:"+data[0]);
				iline++;
			}			
			csvReader.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }   				
	}
	
	void ReadConfigFile()
    {
    	int iline = 0;
    	String row;
    	//String fname="plugins/WindsConfig_"+ConfigOpt+".txt";
    	String fname=ConfigDir+"/WindsConfig_"+ConfigOpt+".txt";
    	int NoWinds = 0;
    	//IJ.log("Reading "+fname);
    	try(
    	    BufferedReader csvReader = new BufferedReader(new FileReader(fname));
    	)
    	{
			while ((row = csvReader.readLine()) != null) 
			{
				String[] data = row.split(" ");
				// do something with the data...
				//IJ.log("line="+iline+" :" + row + "data[0]="+data[0]+", data[1]="+data[1]);	
				/*
				else if (data[0].compareTo("PluginsDir")==0)
					PluginsDir = data[1];
				else if (data[0].compareTo("ConfigDir")==0)
					ConfigDir = data[1];
				*/
				if (data[0].compareTo("TracksDir")==0)
					TracksDir = data[1];
				else if (data[0].compareTo("SitesDir")==0)
					SitesDir = data[1];
				else if (data[0].compareTo("DataDir")==0)
					DataDir = data[1];
				else if (data[0].compareTo("LaunchSitesFname")==0)
				{
					LaunchSitesFname = data[1];
				    if (!data[1].contains("/"))
				    	LaunchSitesFname = ConfigDir + "/" + LaunchSitesFname;					
				}
				else if (data[0].compareTo("LandingSitesFname")==0)
				{
					LandingSitesFname = data[1];
				    if (!data[1].contains("/"))
				    	LandingSitesFname = ConfigDir + "/" + LandingSitesFname;					
				}
				else if (data[0].compareTo("GroundStationsFname")==0)
				{
					GroundStationsFname = data[1];
				    if (!data[1].contains("/"))
				    	GroundStationsFname = ConfigDir + "/" + GroundStationsFname;					
				}
				else if (data[0].compareTo("GeodesicFname")==0)
				{
					GeodesicFname = data[1];
				    if (!data[1].contains("/"))
				    	GeodesicFname = ConfigDir + "/" + GeodesicFname;					
				}
				
				else if (data[0].compareTo("WindFilesList")==0)
				{
					WindFilesList = data[1];
				    if (!data[1].contains("/"))
						WindFilesList = DataDir + "/" + WindFilesList;
				}
				else if (data[0].compareTo("FileUfname")==0)
				{
					FileUfname = data[1];
				    //if (!data[1].contains("/"))
				    if ( (data[1].charAt(0)!='/') && (data[1].charAt(1)!=':') )
						FileUfname = DataDir + "/" + FileUfname;
				}
				else if (data[0].compareTo("FileVfname")==0)
				{
					FileVfname = data[1];
				    //if (!data[1].contains("/"))
				    if ( (data[1].charAt(0)!='/') && (data[1].charAt(1)!=':') )
						FileVfname = DataDir + "/" + FileVfname;
				}
				
				else if (data[0].compareTo("PlotGrid")==0)
					PlotGrid = Integer.parseInt(data[1]);
				else if (data[0].compareTo("PlotBalloonPos")==0)
				{
					NoWinds = Integer.parseInt(data[1]);
					if (NoWinds>0) PlotBalloonsPos = true;
				}
				else if (data[0].compareTo("ShowMultiTracks")==0)
				{
					NoWinds = Integer.parseInt(data[1]);
					if (NoWinds>0) ShowMultiTracks = true;
				}
				else if (data[0].compareTo("ShowSites")==0)
				{
					int iShowSites = Integer.parseInt(data[1]);
					if (iShowSites>0) ShowSites= true;
				}
				else if (data[0].compareTo("arrowScale")==0)
					arrowScale = Float.parseFloat(data[1]);
				else if (data[0].compareTo("headScale")==0)
					headScale = Float.parseFloat(data[1]);
				else if (data[0].compareTo("TargetEndLat")==0)
					TargetEndLat = Float.parseFloat(data[1]);
				else if (data[0].compareTo("TargetEndLon")==0)
					TargetEndLon = Float.parseFloat(data[1]);
				else if (data[0].compareTo("MetNumX")==0)
					ConfigMetNumX = Integer.parseInt(data[1]);
				else if (data[0].compareTo("MetNumY")==0)
					ConfigMetNumY = Integer.parseInt(data[1]);
				else if (data[0].compareTo("MetCellSize")==0)
					ConfigMetCellSize = Float.parseFloat(data[1]);
				else if (data[0].compareTo("MetTimeStep")==0)
					ConfigMetTimeStep = Integer.parseInt(data[1]);
				else if (data[0].compareTo("MaxSplitIntervals")==0)
					MaxSplitIntervals = Integer.parseInt(data[1]);
				else if (data[0].compareTo("BalloonLaunchTime")==0)
					BalloonLaunchTime = Integer.parseInt(data[1]);
				else if (data[0].compareTo("SelectedMultiTrackFile")==0)
					SelectedMultiTrackFile = Integer.parseInt(data[1]);
				else if (data[0].compareTo("SelectedLaunchSite")==0)
					SelectedLaunchSite = Integer.parseInt(data[1]);
				else if (data[0].compareTo("SelectedLandingSite")==0)
					SelectedLandingSite = Integer.parseInt(data[1]);
				else if (data[0].compareTo("DialogXpos")==0)
					DialogXpos = Integer.parseInt(data[1]);
				else if (data[0].compareTo("DialogYpos")==0)
					DialogYpos = Integer.parseInt(data[1]);
				else
					IJ.log("Unknown Config setting:"+data[0]);
				iline++;
			}			
			csvReader.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }   
        
        // Parse FileUfname to get StartLaunchDate/Time
        int yrpos = FileUfname.indexOf("_20")+1;
        int yrpos2 = FileUfname.indexOf("/20")+1;
        if (yrpos>0)
        {
        	String strYear = FileUfname.substring(yrpos,yrpos+4);
        	String strMMDD = "0101";
        	if (FileUfname.length()>yrpos+8)
        	   strMMDD = FileUfname.substring(yrpos+4,yrpos+8);
        	//IJ.log("strYear="+strYear+", strMMDD="+strMMDD);
        	if (strMMDD.matches("\\d+"))
        	{
        		strStartLaunchDate = strYear + strMMDD;
        	}
        	else
        	{
         		strStartLaunchDate = strYear + "0101";
         	}
        }
        else if (yrpos2>0)
        {
        	// Assume grib2-derived file name of type 20xx-Mmm-U.bin
        	String chMonthName = FileUfname.substring(yrpos2+5,yrpos2+7);
        	String chMonths="JanFebMarAprMayJunJulAugSepOctNovDec";
        	int iMonth = chMonths.indexOf(chMonthName)/3 + 1;
        	String chMonthNum = String.format("%02d",iMonth);
        	strStartLaunchDate = FileUfname.substring(yrpos2,yrpos2+4)+chMonthNum+"01";
        	//IJ.log("chMonthName="+chMonthName+", iMonth="+iMonth+", chMonthNum="+chMonthNum);
        }
        else
        	strStartLaunchDate = "UNKNOWN_";
		
        ListDirs(TracksDir);
    }
	
	void ReadLaunchSitesFile(String fname)
    {
    	int iline = 0;
    	String row;
    	NumLaunchSites = 0;
    	try(
    	    BufferedReader csvReader = new BufferedReader(new FileReader(fname));
    	)
    	{
			while ((row = csvReader.readLine()) != null) 
			{
				String[] data = row.split("[, :=]");
				// do something with the data ...
				LaunchName[iline] = data[0];	
				LaunchLat[iline] = Float.parseFloat(data[1]);	
				LaunchLon[iline] = Float.parseFloat(data[2]);	
				fracHeight[iline] = 0.0;
				EndLat[iline] = 0.0;
				EndTime[iline] = 0.0;
				iline++;
				NumLaunchSites++;
			}
			csvReader.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }   
    }
    
	void ReadLandingSitesFile(String fname)
    {
    	int iline = 0;
    	String row;
    	NumLandingSites = 0;
    	try(
    	    BufferedReader csvReader = new BufferedReader(new FileReader(fname));
    	)
    	{
			while ((row = csvReader.readLine()) != null) 
			{
				String[] data = row.split("[, :=]");
				// do something with the data ...
				LandingName[iline] = data[0];	
				LandingLat[iline] = Float.parseFloat(data[1]);	
				LandingLon[iline] = Float.parseFloat(data[2]);	
				iline++;
				NumLandingSites++;
			}
			csvReader.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }   
        TargetEndLat = LandingLat[0]; 
        TargetEndLon = LandingLon[0]; 
        
        if (TargetEndLon<LaunchLon[0])
        	EastToWest = true;
    }
    
	void ReadGroundStationsFile(String fname)
    {
    	int iline = 0;
    	String row;
    	NumGroundStations = 0;
    	try(
    	    BufferedReader csvReader = new BufferedReader(new FileReader(fname));
    	)
    	{
			while ((row = csvReader.readLine()) != null) 
			{
				String[] data = row.split("[, :=]");
				// do something with the data ...
				GroundStationName[iline] = data[0];	
				GroundStationLat[iline] = Float.parseFloat(data[1]);	
				GroundStationLon[iline] = Float.parseFloat(data[2]);	
				//IJ.log("ReadGroundStations: "+NumGroundStations+", Name="+GroundStationName[iline]+", Lat="+GroundStationLat[iline]+", Lon="+GroundStationLon[iline]);
				iline++;
				NumGroundStations++;
			}
			csvReader.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }   
    }
        
    // Try extrapolating above current max height level to give higher-level winds field
    void ExtrapolateHeights()
    {
    	int TrackHeight = MetNumH - 1; // current top height level
    	int NewMetNumH = MetNumH+1;
    	int ImagesPerHeightLevel = (int)MetNumImages/MetNumH;
    	
    	if (TestGrib2)
    		ImagesPerHeightLevel = (int)MetNumImages;
    	
    	long BuffSize = MetNumX*MetNumY*(MetNumH+1)* ImagesPerHeightLevel;
    	
    	float[] TempU = new float[(int)BuffSize];
    	float[] TempV = new float[(int)BuffSize];
        //IJ.log("ExtrapolateHeights: MetNumImages="+MetNumImages+", MetNumH="+MetNumH+", MetNumX="+MetNumX+", MetNumY="+MetNumY+", BuffSize="+BuffSize);

    	// First remap old array to new array, allowing for extra height level
    	for (int img=0; img<ImagesPerHeightLevel; img++)
    	{
    		for (int iy=0; iy< MetNumY; iy++)
    		{
    			for (int ix=0; ix<MetNumX; ix++)
    			{				
    				for (int ih=0;ih<MetNumH;ih++)
    				{
    				    int oldind;  
    				    int newind;   // array structure with new additional height level
						if (TestGrib2) // u/v winds from binary float file derived from Grib2 inputs
						{
							oldind = ix + MetNumX*iy + MetNumX*MetNumY*(ih*(int)MetNumImages + img);
							newind = ix + MetNumX*iy + MetNumX*MetNumY*(ih*(int)MetNumImages + img);
						}
						else if (MetNumT<=12) // Monthly
						{
							oldind = ix + MetNumX*iy + MetNumX*MetNumY*(ih + MetNumH*img);
							newind = ix + MetNumX*iy + MetNumX*MetNumY*(ih + NewMetNumH*img);
						}
						else if (MetNumT<=366) // Daily
						{
							oldind =  ix + MetNumX*(ih + MetNumH*(MetNumT*iy +  img) );
							newind =  ix + MetNumX*(ih + NewMetNumH*(MetNumT*iy +  img) );
						}
						else // 6-hourly
						{
							oldind =  ix + MetNumX*(ih + MetNumH*(MetNumT*iy + img) );
							newind =  ix + MetNumX*(ih + NewMetNumH*(MetNumT*iy + img) );
						}
						TempU[newind] = allFloatsU[oldind];
						TempV[newind] = allFloatsV[oldind];
    				}
    			}
    		}
    	}
        //IJ.log("ExtrapolateHeights: remapped allFloats[]");
 
    	for (int ind=0;ind<BuffSize;ind++)
    	{
    		allFloatsU[ind] = TempU[ind];
    		allFloatsV[ind] = TempV[ind];
    	}
    	
    	MetNumH++; // increase max height level;
        IJ.log("ExtrapolateHeights: MetNumH="+MetNumH);
  	
    	// Now attempt interpolation
    	for (int img=0; img<ImagesPerHeightLevel; img++)
    	{
    		// Extrapolate up one height level from MetNumH
    		for (int iy=0; iy< MetNumY; iy++)
    		{
    			for (int ix=0; ix<MetNumX; ix++)
    			{
    				int oldind1;  // top height level
    				int oldind2;  // previous height level
    				int newind;   // new additional height level				
    				
					if (TestGrib2) // u/v winds from binary float file derived from Grib2 inputs
					{
						oldind1 = ix + MetNumX*iy + MetNumX*MetNumY*(TrackHeight*(int)MetNumImages + img);
					    newind = oldind1 + MetNumX*MetNumY*(int)MetNumImages;
					    oldind2 = oldind1 - MetNumX*MetNumY*(int)MetNumImages;
					}
					else if (MetNumT<=12) // Monthly
					{
						oldind1 = ix + MetNumX*iy + MetNumX*MetNumY*(TrackHeight + MetNumH*img);
					    newind = oldind1 + MetNumX*MetNumY;
					    oldind2 = oldind1 - MetNumX*MetNumY;
					}
					else if (MetNumT<=366) // Daily
					{
						oldind1 =  ix + MetNumX*(TrackHeight + MetNumH*(MetNumT*iy +  img) );
					    newind = oldind1 + MetNumX;
					    oldind2 = oldind1 - MetNumX;
					}
					else // 6-hourly
					{
						oldind1 =  ix + MetNumX*(TrackHeight + MetNumH*(MetNumT*iy + img) );
					    newind = oldind1 + MetNumX;
					    oldind2 = oldind1 - MetNumX;
					}
						
					//allFloatsU[newind] = TempU[oldind1];
					allFloatsU[newind] = 2*allFloatsU[oldind1]-allFloatsU[oldind2]; // simple linear extrapolation from last two height levels
					//allFloatsV[newind] = TempV[oldind1];
					allFloatsV[newind] = 2*allFloatsV[oldind1]-allFloatsV[oldind2]; // simple linear extrapolation from last two height levels
				}
    		}
    	}
    	
    }
    
    
    void TimeHeightInterpolation(double latind, double lonind, int dataTimeStep, double deltaTime, int TrackHeight, double heightfrac)
    {		
			int ind = 0;
			int ind2 = 0;
            if (TestGrib2) // u/v winds from binary float file derived from Grib2 inputs
            {
			   ind = ((int)lonind + MetNumX*(int)(latind)) + MetNumX*MetNumY*(TrackHeight*(int)MetNumImages + dataTimeStep);
			   ind2 = ind + MetNumX*MetNumY;             	  
            }
			else if (MetNumT<=12) // Monthly
			{
			   ind = ((int)lonind + MetNumX*(int)(latind)) + MetNumX*MetNumY*(TrackHeight + MetNumH*dataTimeStep);
			   ind2 = ind + MetNumX*MetNumY*MetNumH;
			}
            else if (MetNumT<=366) // Daily
            {
               ind =  (int)lonind + MetNumX*(MetNumT*MetNumH*(int)(latind) + TrackHeight + MetNumH*dataTimeStep);
               ind2 = ind + MetNumX*MetNumH;
            }
            else // 6-hourly
            {
               ind =  (int)lonind + MetNumX*(MetNumT*MetNumH*(int)(latind) + TrackHeight + MetNumH*dataTimeStep);
               ind2 = ind + MetNumX*MetNumH;
            }
            
            if (ind2>MetNumX*MetNumY*MetNumT*MetNumH)
            {
            	//IJ.log("*** ind2 Array overflow, TrackHeight="+TrackHeight+", dataTimeStep="+dataTimeStep);
            	//IJ.log("MetNumH="+MetNumH+", TrackHeight="+TrackHeight+", MetNumImages="+MetNumImages+", dataTimeStep="+dataTimeStep);
            	u = allFloatsU[ind];
            	v = allFloatsV[ind];
            }
            else
            {
			   // Get U and V for current time, by temporal interpolation of U and V within current cell in previous and next met data time steps
			   u = (1.0-deltaTime)*allFloatsU[ind] + deltaTime*allFloatsU[ind2];
			   v = (1.0-deltaTime)*allFloatsV[ind] + deltaTime*allFloatsV[ind2];
			}
			
			double frac = heightfrac;
			if ((frac>0)) // && (ApplyFracInterval==CurrSplitInterval)) // get index to height at next higher level
			{
				int ind3 = 0;
				int ind4 = 0;
                if (TestGrib2) // u/v winds from binary float file derived from Grib2 inputs
                {
                   ind3 = ind + MetNumX*MetNumY*(int)MetNumImages;
                   ind4 = ind3 + MetNumX*MetNumY;
              	}
			    else if (MetNumT<=12) // Monthly
			    {
			       ind3 = ind + MetNumX*MetNumY;
			       ind4 = ind3 + MetNumX*MetNumY*MetNumH;
			    }
                else if (MetNumT<=366) // Daily
                {
                   ind3 = ind + MetNumX;
                   ind4 = ind3 + MetNumX*MetNumH;
                }
                else // 6-hourly
                {
                   ind3 = ind + MetNumX;
                   ind4 = ind3 + MetNumX*MetNumH;
                }
                
                if (ind4>MetNumX*MetNumY*MetNumT*MetNumH)
                {
            	   // IJ.log("*** ind4 Array overflow!!!");
            	    u2 = allFloatsU[ind3];
            	    v2 = allFloatsV[ind3];
            	}
                else
                {
			        u2 = (1.0-deltaTime)*allFloatsU[ind3] + deltaTime*allFloatsU[ind4];
			        v2 = (1.0-deltaTime)*allFloatsV[ind3] + deltaTime*allFloatsV[ind4];
                }
				u = (1.0-frac)*u + frac*u2;
				v = (1.0-frac)*v + frac*v2;
			}
	}
	
	// Accumulate longitude distribution of all balloons for current timestep
	void CalcLonDistribution(double lon, double lat,  Color colTrack)
	{
		int LonBinPriority = 0;
		double MinLonBinVal = -70.0;
		double MaxLonBinVal = 0.0;
		double LonBinSize = 10.0; // degrees
		double LonBinMinLat = 35.0;
		double LonBinMaxLat = 65.0;
		
		
		int NumBins = (int)((MaxLonBinVal - MinLonBinVal)/LonBinSize) + 1;
		int LonBin = (int) ((lon-MinLonBinVal)/LonBinSize);
//		if (LonBin<0) LonBin=0;
//		else if (LonBin>NumBins-1) LonBin = NumBins-1;
		
		if ((LonBin>=0) && (LonBin<=NumBins-1))
		{
			if ( (lat>LonBinMinLat) && (lat<LonBinMaxLat) )
			{
				if (colTrack==Color.yellow)
					LonBinPriority=0;
				else if (colTrack==Color.black)
					LonBinPriority=1;
				else if (colTrack==Color.blue)
					LonBinPriority=2;
				else
					LonBinPriority=3;
			
				LonBinCount[LonBinPriority*NumBins + LonBin]++;
		    }
		}
	}
	
	void PlotTracks(PrintWriter writer, Color colTrack, boolean ShowTracks, int site, int imTimeStep, double StartLat, double StartLon, double timestep, int interp, int iheight, double frac, int ApplyFracInterval)
	{
		double x1,y1,x2,y2;
		int Thickness = 2;
		
        double lat = StartLat;
        double newlat = lat;
        double lon = StartLon;
		double latind;
		double lonind;

		int ind1,it;
		int ind;
		int PrevSplitInterval = -1;
		int CurrSplitInterval = 0;
		int CurrMetInterval = 0;
		double ElapsedTime = 0.0;
		double fraclon = -1.0;
		double fheight = 0.0;
		int TrackHeight = iheight; // Assume we start with nominal user-defined height level
		int dataTimeStep = imTimeStep; // Assume we start with imTimstep for selected launch date/time
        //IJ.log("iheight="+iheight+", imTimeStep="+imTimeStep+", ApplyFrac="+ApplyFracInterval);
        int ReachedLanding = 0;
        
        double LaunchHours = imTimeStep*MetTimeStep/NumMetSubSteps;
        
        int PrevGeodesicLonIndex = 0;
        for (int geoInd=0;(geoInd<NumGeodesic) && (geolon[geoInd]<StartLon);geoInd++)
        	PrevGeodesicLonIndex = geoInd;
        	
        if ((colTrack==Color.yellow) && LogYellow)
        	IJ.log("Yellow Track, imTimeStep="+imTimeStep+", LaunchHours="+LaunchHours+", MaxTimeSteps="+MaxTimeSteps+", iheight="+iheight+", timestep="+timestep+", TimeStep="+TimeStep);
        
        GeodesicSumSq = 0.0;        
		for (it=0;(it<MaxTimeSteps)&&(ReachedLanding==0);it++)
		{
		  if ((lon<maxlon) && (lon>minlon) && (lat>minlat) && (lat<maxlat))
		  {
		  	//DeltaMetSubStep = (double)MetSubStep/NumMetSubSteps - (double)imTimeStep;
			ElapsedTime = TimeStep*it + DeltaMetSubStep*MetTimeStep;  // Elapsed time in hours			
			CurrSplitInterval = (int) (ElapsedTime/SplittingInterval);
		  	
		  	if (CurrSplitInterval>PrevSplitInterval)
		  	{
		  		// Entered new Splitting Interval
		  		// Get height Level for specified Splitting Interval
		  		TrackHeight = (int)IntervalHeight[CurrSplitInterval];
		  		//IJ.log("New Splitting interval, it="+it+", Time="+ElapsedTime+", Interval="+CurrSplitInterval+", TrackHeight="+TrackHeight+", imTimeStep="+imTimeStep);
		  		PrevSplitInterval = CurrSplitInterval;
		  	}
		  	
		  	double dTime = 0.0;
		  	double deltaTime = 0.0;
		  	double deltaTimeStep = 0.0;
		  	CurrMetInterval = 0;
		  	deltaTime = 0;
		  	dataTimeStep = imTimeStep;
		  	if (UseTimeVarying)
		  	{
		  		dTime = ((double)imTimeStep/NumMetSubSteps)*MetIntervalMultiplier;
		  		deltaTimeStep = dTime - (int) dTime;
		  	    CurrMetInterval = (int) (MetIntervalMultiplier*ElapsedTime/MetTimeStep);
		  	    dataTimeStep = (imTimeStep/NumMetSubSteps)*MetIntervalMultiplier + (int)CurrMetInterval;		  	    
		  	    deltaTime = MetIntervalMultiplier*ElapsedTime/MetTimeStep - CurrMetInterval;  // fraction of MetTimeStep in range 0.0 to 1.0
		  	}
		  	//IJ.log("*** it="+it+", MaxTimeHours="+MaxTimeHours+", ElapsedTime="+ElapsedTime+", dataTimeStep="+dataTimeStep+", CurrMetInterval="+CurrMetInterval+", deltaTimeStep="+deltaTimeStep+", deltaTime="+deltaTime+", UseTimeVarying="+UseTimeVarying);
		  	
			latind = (90.0-lat)/MetCellSize;
			lonind = (180.0+lon)/MetCellSize;
            if (TestGrib2) // u/v winds from binary float file derived from Grib2 inputs
            {
               lonind = (lon-minlon)/MetCellSize;
               latind = (maxlat-lat)/MetCellSize;               
 			   if (dataTimeStep>(MetNumImages-1))
 			   {
				   CurrMetInterval = 0;
				   dataTimeStep = (int) MetNumImages-1;
			   }
           }
           
		   if (TrackHeight>=MetNumH-1)
		   {
			   TrackHeight = MetNumH-1;
			   frac = 0;
		   }
            
//            if (colTrack==Color.yellow)
//            	IJ.log("AAA: it="+it+", imTimeStep="+imTimeStep+", ElapsedTime="+ElapsedTime+", CurrMetInterval="+CurrMetInterval+", dataTimeStep="+dataTimeStep+", deltaTime="+deltaTime+", latind="+latind+", lonind="+lonind);
//            if (TrackHeight<MetNumH-1)
//				IJ.log("it="+it+",  dataTimeStep="+dataTimeStep+", CurrMetInterval="+CurrMetInterval+", MaxTimeSteps="+MaxTimeSteps+", MetNumImages="+MetNumImages);
			TimeHeightInterpolation(latind,lonind, dataTimeStep, deltaTime, TrackHeight, frac);

			fheight = TrackHeight + frac;
			double uDegsPerHour = u*60*60*360/(Math.cos(lat*DegToRad)*EarthSize);
			double vDegsPerHour = v*60*60*360/EarthSize;
			double newlon = lon + uDegsPerHour*timestep;
			newlat = lat + vDegsPerHour*timestep;
			
//			if ((ShowTracks) && (imTimeStep==135) && (colTrack==Color.orange))
//			   IJ.log("it="+it+", newlon="+newlon+", newlat="+newlat+", fheight="+fheight+", CurrSplit="+CurrSplitInterval+", ht[]="+IntervalHeight[CurrSplitInterval]);		   
			//IJ.log("lat="+lat+", latind="+latind+", lon="+lon+", lonind="+lonind+", u="+u+", v="+v+" m/s, newlat="+newlat+", newlon="+newlon);
			//IJ.log("uDeg/Hr="+uDegsPerHour+", vDeg/Hr="+vDegsPerHour);
			
			boolean LonCheck = false;			
			if (EastToWest)
			{
				if ( (newlon<=TargetEndLon) && (lon>TargetEndLon)) LonCheck = true;
			}
			else
			{
				if ( (newlon>=TargetEndLon) && (lon<TargetEndLon)) LonCheck = true;
			}
			
			if ( (LonCheck) && (Math.abs(newlon-lon)<20.0) &&  (Math.abs(newlat-lat)<20.0))
			{
				fraclon = 1.0 - (newlon-TargetEndLon)/(newlon-lon);
				if (colTrack!=Color.black)
				{
				   EndLat[site] = lat + fraclon*(newlat-lat);
				   newEndLon = lon + fraclon*(newlon-lon); 
				}
				if (colTrack==Color.yellow)
				{
					newlat = lat + fraclon*(newlat-lat);
					newlon = TargetEndLon;
				}
				EndTime[site] = TimeStep*((it-1) + fraclon);
			    DlatAtMinDlat = EndLat[site] - TargetEndLat;
				ReachedLanding = 1;
				//IJ.log("it="+it+", lon="+lon+", newlon="+newlon+", lat="+lat+", newlat="+newlat+", col="+colTrack);
				//IJ.log("site="+site+", height="+fheight+", EndLat="+EndLat[site]+", EndTime="+EndTime[site]+" hrs");
				//IJ.log("h0="+(int)IntervalHeight[0]+", h1="+(int)IntervalHeight[1]+", h2="+IntervalHeight[2]+", h3="+IntervalHeight[3]+", h4="+IntervalHeight[4]+", CurrSplit="+CurrSplitInterval+", ApplySplit="+ApplyFracInterval+", frac="+frac+", EndLat="+EndLat[site]+", EndTime="+EndTime[site]+" hrs");
			}
			
			
			if (((ShowTracks) && (colTrack!=Color.blue)) || ((colTrack==Color.blue) && !(PlotOnlyGoodTracks)))
			{
				x1 = inxsize*(lon - minlon)/(maxlon-minlon);
				y1 = inysize*(maxlat - lat)/(maxlat-minlat);
				x2 = inxsize*(newlon - minlon)/(maxlon-minlon);
				y2 = inysize*(maxlat - newlat)/(maxlat-minlat);
//				if ((IntervalHeight[0]==15) && (IntervalHeight[1]==12))
//				   IJ.log("it="+it+", time="+ElapsedTime+", MetInd="+CurrMetInterval+", CurrSplit="+CurrSplitInterval+", ApplySplit="+ApplyFracInterval+", frac="+frac+", lat="+lat+", lon="+lon+", x2="+x2+", y2="+y2);
				Line newline1 = new Line((int)x1,(int)y1,(int)x2,(int)y2);
				newline1.setStroke(new BasicStroke(Thickness));
				newline1.setStrokeColor(colTrack);
				overlay.add(newline1); 
				
				if (colTrack==Color.black)
				{
					// get closest separation from geodesic at this (lat,lon)
					int GeodesicLat_atThisLon = 0;
					int prevInd = PrevGeodesicLonIndex;
					PrevGeodesicLonIndex = GeodesicLat_atThisLon;
                    for (int geoInd=prevInd;(geoInd<NumGeodesic) && (geolon[geoInd]<newlon);geoInd++)
        	             GeodesicLat_atThisLon = geoInd;
					
					// get dlat and sumsq along track
					double dLat = geolat[GeodesicLat_atThisLon+1] - newlat;					
					GeodesicSumSq += dLat*dLat;
					
					if ( (OutputAnyTrackDetails) && (OutputBestTrackDetails))
					{
						double kmHeight =  0.0;
						int iHeight = (int)fheight;
						double hfrac = fheight-iHeight;
						if ((iHeight>=0) && (iHeight<MetNumH-1))
						{
						   if (TestGrib2)
						      kmHeight = (1.0-hfrac)*HeightGrib2Km[iHeight] + hfrac*HeightGrib2Km[iHeight+1];
					       else
						      kmHeight = (1.0-hfrac)*HeightKm[iHeight] + hfrac*HeightKm[iHeight+1];
						}
					    else if (iHeight>0)
					    {
						    if (TestGrib2)
							    kmHeight = HeightGrib2Km[MetNumH-1];
						    else
							    kmHeight = HeightKm[MetNumH-1];
					    }
						else
							IJ.log("** Invalid Height: TrackHeight="+TrackHeight+", fheight="+fheight+", iHeight="+iHeight);
						   
						String outputLine;
						if (it==0)
						{
						   outputLine = String.format("%6.1f,%3d,%6.1f,%6.2f,%8.3f,%8.3f",LaunchHours,countValidTracks,ElapsedTime,lat,lon,kmHeight);
						   writer.println(outputLine);
						}
						outputLine = String.format("%6.1f,%3d,%6.1f,%6.2f,%8.3f,%8.3f",LaunchHours,countValidTracks,ElapsedTime+TimeStep,newlat,newlon,kmHeight);
						writer.println(outputLine);
					}
				}
				
				if ((colTrack==Color.yellow) && (OutputBestTrackDetails))
				{
					//IJ.log("Launch="+imTimeStep+", time="+ElapsedTime+"lat="+newlat+", lon="+newlon+", height="+fheight);
					double kmHeight =  0.0;
					int iHeight = (int)fheight;
					double hfrac = fheight-iHeight;
					if ((iHeight>=0) && (iHeight<MetNumH-1))
					{
					   if (TestGrib2)
					      kmHeight = (1.0-hfrac)*HeightGrib2Km[iHeight] + hfrac*HeightGrib2Km[iHeight+1];
				       else
					      kmHeight = (1.0-hfrac)*HeightKm[iHeight] + hfrac*HeightKm[iHeight+1];
					}
					else if (iHeight>0)
					{
						if (TestGrib2)
							kmHeight = HeightGrib2Km[MetNumH-1];
						else
							kmHeight = HeightKm[MetNumH-1];
					}
					else
						IJ.log("** Invalid Height: TrackHeight="+TrackHeight+", fheight="+fheight+", iHeight="+iHeight);
				   	   
					String outputLine;
					int TrackID = 0;
					
					if (YearlyBestTracks)
						TrackID = countYellowTracks;
					else
						TrackID = countValidTracks;
					
					if (it==0)
					{
						outputLine = String.format("%6.1f,%3d,%6.1f,%6.2f,%8.3f,%8.3f,%8.1f",LaunchHours,TrackID,ElapsedTime,lat,lon,kmHeight,MinSumSq);
						writer.println(outputLine);
					}
					outputLine = String.format("%6.1f,%3d,%6.1f,%6.2f,%8.3f,%8.3f,%8.1f",LaunchHours,TrackID,ElapsedTime+TimeStep,newlat,newlon,kmHeight,MinSumSq);
					writer.println(outputLine);
				}
				
				if (AlongTrackWinds)
				   DrawArrow(x2, y2, (x2+arrowScale*u), (y2-arrowScale*v), Color.red);
            }
            lat = newlat;
            lon = newlon;
            CalcLonDistribution(lon,lat,colTrack);
          }
        }
       
        newEndLat = lat + fraclon*(newlat-lat);
        Dlat = newEndLat - TargetEndLat;
        AbsDlat = Dlat;
		if (AbsDlat<0) AbsDlat=-AbsDlat;
		if (colTrack!=Color.black)
			EndLat[site] = newEndLat;
		
		lon = TargetEndLon;
		EndLon[site] = lon;

//		IJ.log("YYYa: EndTime[site]="+EndTime[site]);		
		String outputLine = String.format("%3d, %3d, Ht=%5.2f (%2d), EndTime=%6.2f, %6.2f, EndLat=%6.2f, %6.2f, EndLon=%6.2f, Dlat=%6.2f, fraclon=%6.2f GeoSq=%8.1f",imTimeStep,countValidTracks,fheight,TrackHeight,ElapsedTime,EndTime[site],newEndLat,lat,lon,Dlat,fraclon,GeodesicSumSq);
        String LogHeightIntervals = ", CurrSplit="+CurrSplitInterval+", h[]="+IntervalHeight[0]+","+IntervalHeight[1]+","+IntervalHeight[2]+IntervalHeight[3]+","+IntervalHeight[4]+","+IntervalHeight[5]+","+IntervalHeight[6];
        
        if (colTrack==Color.yellow)
        {
        	countYellowTracks++;
        	if (LogYellow)
         	   IJ.log("Yellow Track "+outputLine+LogHeightIntervals);
        }
        if (colTrack==Color.black) 
        {
        	countBlackTracks++;
        	if (LogBlack)
         	   IJ.log("Black Track "+outputLine+LogHeightIntervals);
        }
        else if (colTrack==Color.blue) 
        {
        	countBlueTracks++;
        	if (LogBlue)
        	   IJ.log("Blue Track "+outputLine+LogHeightIntervals);
        }
        else if (colTrack==Color.orange)
        {
        	countOrangeTracks++;
        	if (LogOrange)
        	   IJ.log("Orange Track "+outputLine+LogHeightIntervals); 
        }
        else if (colTrack==Color.cyan)
        {
        	countCyanTracks++;
        }
        
	}
	
	void ReadTrackBalloons(String rowList)
	{
		int ind = 0;
		int indStart =0;
		int itStart = 0;
		float currLaunchTime = 0;
		float prevLaunchTime = 0;
		float startLaunchTime = 0;
		float currStepTime = 0;
		float prevStepTime = 0;
		double fStart = 0.0;
    	String row;

    	IJ.log("Tracks file = "+rowList);
		String fname = rowList;

			   IJ.log("Reading file: "+fname);
			   try(
				   BufferedReader csvReader = new BufferedReader(new FileReader(fname));
			   )
			   {
				   ind = itStart*MaxTrackHours;
				   indStart = ind;
				   int icount=0;
				   
				   row = csvReader.readLine(); // skip header line
				   //IJ.log("File = "+fname);
				   while ((row = csvReader.readLine()) != null) 
				   {
					    //IJ.log("row = "+row);
					    String[] data = row.split("[,%n]");	
					    //IJ.log("data[3]="+data[3]+", data[4]="+data[4]+", data[5]="+data[5]);					   
					   currLaunchTime = Float.parseFloat(data[0]); 
					   
						  if ((ind>0) && (currLaunchTime!=prevLaunchTime))
						  {
					   	      BalloonLaunchTimes[itStart] = prevLaunchTime - startLaunchTime; // in hours
					   	      if ( DebugBalloons )  
					   	      	  IJ.log("itStart="+itStart+", BalloonLaunchTimes[itStart]="+BalloonLaunchTimes[itStart]+", prevLaunchTime="+prevLaunchTime+", currLaunchTime="+currLaunchTime+", ind="+ind);
							  
							  if (currLaunchTime>startLaunchTime)
							  {
							     fStart = (currLaunchTime - startLaunchTime)/IntegTimeStep;
							     itStart = (int) fStart;
							  }
							  indStart = itStart*MaxTrackHours;
							  ind = indStart;
							  if ( DebugBalloons )  
							  	  IJ.log("itStart="+itStart+", indstart="+indStart+", IntegTimeStep="+IntegTimeStep+", fStart="+fStart ); 
						  }
						  else if (currStepTime - prevStepTime>0)
						  {
                              IntegTimeStep = currStepTime - prevStepTime;
						  	  prevStepTime = currStepTime;
						  }

						  if ( (ind-indStart<MaxTrackHours) && (ind>=0) && (ind<MaxTrackHours*MaxBalloonLaunchTimes) )
						   {
							  currStepTime = Float.parseFloat(data[2]);	
							  BalloonLat[ind] = Float.parseFloat(data[3]);	
							  BalloonLon[ind] = Float.parseFloat(data[4]);	
							  BalloonHt[ind] = Float.parseFloat(data[5]);
							  if ( (DebugBalloons) && (icount<15) )
							  	  IJ.log("icount="+icount+", ind="+ind+", itStart="+itStart+", BalloonLat[ind]="+BalloonLat[ind]+", BalloonLon[ind]="+BalloonLon[ind]+", CurrLauchTime="+currLaunchTime+", prevLaunchTime="+prevLaunchTime );
							  ind++;						  
						   }						  
						  prevLaunchTime = currLaunchTime;

					   icount++;
				   }
				   
				   if (itStart>MaxBalloonTimeSteps)
				   	   if ( (DialogMaxBalloonTimeSteps==0) || (itStart<=DialogMaxBalloonTimeSteps) )
				          MaxBalloonTimeSteps = itStart;
				   StartBalloonLaunchTime = startLaunchTime;
				   csvReader.close();
			   } catch (IOException ex) {
				   ex.printStackTrace();
			   }
	}
	
	void LoadBestTracks(String TrackListFname)
	{
		
		for (int it=0;it<MaxBalloonLaunchTimes;it++)
			BalloonLaunchTimes[it] = -1; // default no launch at each potential launch time

        IJ.log("LoadBestTracks called, TrackListFname="+TrackListFname);
        BalloonLaunchTimes[0] = 0;
        MaxBalloonTimeSteps = 0;
        
        if (MultiTrackFiles[SelectedMultiTrackFile].contains("MultiTrack"))
        {
			// Loop over all Tracks defined in files listed in TracksList.txt file
			String rowList;
			try(
				BufferedReader ListReader = new BufferedReader(new FileReader(TrackListFname));
			)
			{
				while ((rowList = ListReader.readLine()) != null) 
				{			
					ReadTrackBalloons(rowList);
				}
			} catch (IOException ex) {
					   ex.printStackTrace();
			}            
        }
        else // single (yearly) tracks file specified
        	ReadTrackBalloons(MultiTrackFiles[SelectedMultiTrackFile]);
	}
	
	void ReadGribWinds(int AllocMemSize)
	{		
		MetTimeStep = 3; // assume 3-hourly data as default (changeable in config file)
		MetNumH = 15;
		StartHeight = 4; // 15.8 kms
		EndHeight = 15;  // =11 for 25.9 kms, or = 15 for 30 kms!

	    fileSizeU = new File(FileUfname).length();
	    fileSizeFloat = fileSizeU/4;
		int NumValsPerHeight = (int)fileSizeFloat/MetNumH;

        long SizePerHeightLevel = fileSizeFloat/MetNumH;
        if (DoExtrapolateHeights)
        	fileSizeFloat += SizePerHeightLevel; // Allow for one extra (extrapolated) height level
		
		MetCellSize = 1.0; // assume 1-degree data as default (changeable in config file)
		MetNumX = (int)((maxlon-minlon)/MetCellSize) + 1; 
		MetNumY = (int)((maxlat-minlat)/MetCellSize) + 1;		
		
		if (ConfigMetTimeStep>0)
			MetTimeStep = ConfigMetTimeStep;
		if (ConfigMetNumX>0)
			MetNumX = ConfigMetNumX;
		if (ConfigMetNumY>0)
			MetNumY = ConfigMetNumY;
		if (ConfigMetCellSize>0)
			MetCellSize = ConfigMetCellSize;
		
	   if (AllocMemSize==0)
	   {
          allFloatsU = new float[(int)fileSizeFloat];
          allFloatsV = new float[(int)fileSizeFloat];
          float fNum = fileSizeFloat/(MetNumX*MetNumY*MetNumH);
          //IJ.log("fileSizeFloat="+fileSizeFloat+", MetNumH="+MetNumH+", fNum="+fNum);
          MetNumImages = (int) fNum;
       }
       else if (AllocMemSize>0)
	   {
	   	   int deltaH = 0;
          if (DoExtrapolateHeights)
          {
            SizePerHeightLevel = AllocMemSize/MetNumH;
            AllocMemSize += SizePerHeightLevel; // Allow for one extra (extrapolated) height level
            deltaH = 1;
          }
          allFloatsU = new float[AllocMemSize];
          allFloatsV = new float[AllocMemSize];
          float fNum1 = MetNumX*MetNumY*(MetNumH+deltaH);  
          float fNum = AllocMemSize/fNum1;
          MetNumImages = (int) fNum;
          //IJ.log("AllocMemSize="+AllocMemSize+", MetNumH="+MetNumH+", fNum1="+fNum1+", fNum="+fNum+", SizePerHeightLevel="+SizePerHeightLevel);
       }
       MetNumT = (int) MetNumImages;
	   //IJ.log("ConfigMetNumX="+ConfigMetNumX+", maxlon="+maxlon+", minlon="+minlon+", MetNumX="+MetNumX+", MetNumY="+MetNumY+", MetCellSize="+MetCellSize+", MetNumImages="+MetNumImages );
       double dMaxInd = MetNumH*MetNumImages*MetNumX*MetNumY;
       long MaxInd = (long) dMaxInd;
       //IJ.log("MaxInd="+MaxInd+", GribOffset="+GribOffset);
       
       byte[] buf = new byte[(int)fileSizeU];
       try{
       	    //IJ.log("opening file "+FileUfname+", AllocMemSize="+AllocMemSize+", GribOffset="+GribOffset);
            DataInputStream inp = new DataInputStream(new FileInputStream(FileUfname));
		    inp.read(buf);
		    int i=0;
		    int outind = GribOffset;
		    for (int iheight=0; iheight<MetNumH; iheight++)
		    {
		    	outind = (int)(iheight*MetNumImages*MetNumX*MetNumY) + (int)GribOffset;
		    	for (int ind=0; ind<NumValsPerHeight; ind++)
		    	{
		    	    int ival = (buf[4 * i + 3] & 0xFF) | ((buf[4 * i + 2] & 0xFF)<< 8) | ((buf[4 * i + 1] & 0xFF) <<16) | (buf[4 * i]<<24);
		    	    allFloatsU[outind] = Float.intBitsToFloat(ival);
		    	    i++;
		    	    outind++;
		    	}
		    }
	   } catch (IOException err){}
       try{
            DataInputStream inp = new DataInputStream(new FileInputStream(FileVfname));
		    inp.read(buf);
		    int i=0;
		    int outind = GribOffset;
		    for (int iheight=0; iheight<MetNumH; iheight++)
		    {
		    	outind = (int)(iheight*MetNumImages*MetNumX*MetNumY) + (int)GribOffset;
		    	for (int ind=0; ind<NumValsPerHeight; ind++)
		    	{
		    	    int ival = (buf[4 * i + 3] & 0xFF) | ((buf[4 * i + 2] & 0xFF)<< 8) | ((buf[4 * i + 1] & 0xFF) <<16) | (buf[4 * i]<<24);
		    	    allFloatsV[outind] = Float.intBitsToFloat(ival);
		    	    i++;
		    	    outind++;
		    	}
		    }	    
            GribOffset += NumValsPerHeight;
	   } catch (IOException err){}	
	   //IJ.log("Successfully read ugrd.bin & vgrd.bin files");

	   TestGrib2 = true;
	}
	
    void ReadWinds()
    {
    	byte[] allBytesU = {0};
    	byte[] allBytesV = {0};
    	int it,ih,ix,iy;
    	int ind,ind0;
    	int nx,ny,nh;
    	float[] LineBuffU = new float[MetNumX];
    	float[] LineBuffV = new float[MetNumX];

        TestGrib2 = false;
 
        try (
            java.io.RandomAccessFile rFileU = new java.io.RandomAccessFile(FileUfname,"r");
            FileChannel inChannelU = rFileU.getChannel();
            java.io.RandomAccessFile rFileV = new java.io.RandomAccessFile(FileVfname,"r");
            FileChannel inChannelV = rFileV.getChannel();
        ) {
 
            fileSizeU = new File(FileUfname).length();
            fileSizeFloat = fileSizeU/4;
             
            MetNumImages = fileSizeFloat/MetNumX/MetNumY;
            MetNumT = (int) MetNumImages/MetNumH;
            if (MetNumT<=12) // Monthly
            {
              	MetTimeStep = 24.0*31.0;
            }
            else if (MetNumT<=366) // Daily
            {
                MetTimeStep = 24.0;
            }
            else // 6-hourly
            {
                MetTimeStep = 6.0;   
            }
           
            //IJ.log("fileSizeFloat="+fileSizeFloat+", MetNumImages="+MetNumImages+", MetNumH="+MetNumH+", MetNumT="+MetNumT+", MetNumX="+MetNumX+", MetNumY="+MetNumY+", MetTimeStep="+MetTimeStep);
            long SizePerHeightLevel = fileSizeFloat/MetNumH;
            if (DoExtrapolateHeights)
            	fileSizeFloat += SizePerHeightLevel; // Allow for one extra (extrapolated) height level
            //IJ.log("fileSizeFloat="+fileSizeFloat+", MetNumImages="+MetNumImages+", MetNumH="+MetNumH+", MetNumT="+MetNumT+", MetNumX="+MetNumX+", MetNumY="+MetNumY+", MetTimeStep="+MetTimeStep);        
            
            fileSizeU = fileSizeFloat*4;
            ByteBuffer buf_inU = ByteBuffer.allocate((int)fileSizeU);
            buf_inU.clear();

            fileSizeV = fileSizeU;
            ByteBuffer buf_inV = ByteBuffer.allocate((int)fileSizeV);
            buf_inV.clear();

            inChannelU.read(buf_inU);
            inChannelV.read(buf_inV);
 
            allFloatsU = new float[(int)fileSizeFloat];
            allFloatsV = new float[(int)fileSizeFloat];
            buf_inU.rewind();
            buf_inU.asFloatBuffer().get(allFloatsU);            
            buf_inV.rewind();
            buf_inV.asFloatBuffer().get(allFloatsV);            
 
            // Need to re-order data so that 2d array is in order: x: 0=-180degs lon, y: 0=90.0 degs lat 
            nx = MetNumX;
            ny = MetNumY;
            nh = MetNumH;
            int nx2 = nx/2;
            for (it=0;it<MetNumT;it++)
            {
            	for (ih=0;ih<nh;ih++)
            	{
            		for (iy=0;iy<ny;iy++)
            		{
            			ind = (it*nh+ih)*nx*ny + iy*nx;

            			for (ix=0;ix<nx;ix++)
            			{
            			   LineBuffU[ix] = allFloatsU[ind+ix];
            			   LineBuffV[ix] = allFloatsV[ind+ix];
            			}
           			
            			for (ix=0;ix<nx2;ix++)
            			{
            				allFloatsU[ind+ix] = LineBuffU[ix+nx2];
            				allFloatsV[ind+ix] = LineBuffV[ix+nx2];
            			}
            			for (ix=nx2;ix<nx;ix++)
            			{
            				allFloatsU[ind+ix] = LineBuffU[ix-nx2];
            				allFloatsV[ind+ix] = LineBuffV[ix-nx2];
            			}
            		}
            	}
            }
            //IJ.log("Successfully read winds file");
        } catch (IOException ex) {
            ex.printStackTrace();
        }   
        
    }
   
    void DrawArrow(double x1, double y1, double x2, double y2, Color col)
    {
      int lineWidth = 1;
      double size = headScale*100/4.0;
      double dx = x2-x1;
      double dy = y2-y1;
      double ra = Math.sqrt(dx*dx + dy*dy);
      if (ra>0)
      {
         dx /= ra;
         dy /= ra;
         double x3 = (x2-dx*size);
         double y3 = (y2-dy*size);
         double r = 0.3*size;
         double x4 = (x3+dy*r);
         double y4 = (y3-dx*r);
         double x5 = (x3-dy*r);
         double y5 = (y3+dx*r);
         Line newline1 = new Line(x1,y1,x2,y2);
         newline1.setStrokeColor(col);
         overlay.add(newline1);
         Line newline2 = new Line(x2,y2,x4,y4);
         newline2.setStrokeColor(col);
         overlay.add(newline2);
         Line newline3 = new Line(x2,y2,x5,y5);
         newline3.setStrokeColor(col);
         overlay.add(newline3);
      }
    }
   
    void DrawLLseq(int Num,  Color col, int Thickness)
    {
    	int i;
    	double x1,y1,x2,y2;
    	for (i=0;i<Num;i++)
    	{
    		x1 = inxsize*(geolon[i] - minlon)/(maxlon-minlon);
    		y1 = inysize*(maxlat - geolat[i])/(maxlat-minlat);
    		x2 = inxsize*(geolon[i+1] - minlon)/(maxlon-minlon);
    		y2 = inysize*(maxlat - geolat[i+1])/(maxlat-minlat);
    		//IJ.log("lat="+lat[i]+", lon="+lon[i]+", x1="+x1+", y1="+y1);
            Line newline1 = new Line((int)x1,(int)y1,(int)x2,(int)y2);
            newline1.setStroke(new BasicStroke(Thickness));
            newline1.setStrokeColor(col);
            overlay.add(newline1);
    	}
    }

    void PlotSites()
    {
		for (int ind=0; ind<NumLaunchSites; ind++)
		{
			double x1 = inxsize*(LaunchLon[ind] - minlon)/(maxlon-minlon);
			double y1 = inysize*(maxlat - LaunchLat[ind])/(maxlat-minlat);
			double size = 6.0;
			
			OvalRoi circle = new OvalRoi(x1-size/2,y1-size/2,size,size);
			circle.setStroke(new BasicStroke(2));
			circle.setStrokeColor(Color.blue);
			overlay.add(circle);
			
			String label = LaunchName[ind];
		    Font font = new Font("Arial", Font.PLAIN, 12);
		    Roi textRoi = new TextRoi(x1, y1, label, font);
		    textRoi.setStrokeColor(Color.black);  
		    overlay.add(textRoi);
		}
		for (int ind=0; ind<NumLandingSites; ind++)
		{
			double x1 = inxsize*(LandingLon[ind] - minlon)/(maxlon-minlon);
			double y1 = inysize*(maxlat - LandingLat[ind])/(maxlat-minlat);
			double size = 6.0;
			
			OvalRoi circle = new OvalRoi(x1-size/2,y1-size/2,size,size);
			circle.setStroke(new BasicStroke(2));
			circle.setStrokeColor(Color.orange);
			overlay.add(circle);
			
			String label = LandingName[ind];
		    Font font = new Font("Arial", Font.PLAIN, 12);
		    Roi textRoi = new TextRoi(x1, y1, label, font);
		    textRoi.setStrokeColor(Color.black);  
		    overlay.add(textRoi);		
		}
/*    	
		for (int GroundStation=0; GroundStation<NumGroundStations; GroundStation++)
		{
			double x1 = inxsize*(GroundStationLon[GroundStation] - minlon)/(maxlon-minlon);
			double y1 = inysize*(maxlat - GroundStationLat[GroundStation])/(maxlat-minlat);
			double size = 6.0;
			
			OvalRoi circle = new OvalRoi(x1-size/2,y1-size/2,size,size);
			circle.setStroke(new BasicStroke(2));
			circle.setStrokeColor(Color.black);
			overlay.add(circle);
		} 
*/
    }
    
    void ReadTrackFile(String rowList, int YearlyTracks, Color col)
    {
    	String row;
    	int Thickness = 1;
    	double x1,y1,x2,y2;
    	double prevLon,prevLat,prevHgt;
    	double currLon,currLat,currHgt;
    	float LaunchTime = 0;
    	float prevLaunchTime = 0;
				
				IJ.log("Tracks file = "+rowList);
				
				try(
						BufferedReader FileReader = new BufferedReader(new FileReader(rowList));
					)
				{
					row = FileReader.readLine(); // skip first (header) line
					
					if (rowList.contains("Yearly"))
					{
						YearlyTracks = 1;
					}
					int PointNum = 0;
					prevLat = 0.0;
					prevLon = 0.0;
					while ((row = FileReader.readLine()) != null) 
					{
						String[] cols = row.split(",");
						// Get currLon, currLat
						LaunchTime = Float.parseFloat(cols[0]);	
						currLat = Float.parseFloat(cols[3]);	
						currLon = Float.parseFloat(cols[4]);	
						currHgt = Float.parseFloat(cols[5]);	
						if (PointNum>0)
						{
							if ( (YearlyTracks==0) || (LaunchTime==prevLaunchTime) )
							{
								x1 = inxsize*(prevLon - minlon)/(maxlon-minlon);
								y1 = inysize*(maxlat - prevLat)/(maxlat-minlat);
								x2 = inxsize*(currLon - minlon)/(maxlon-minlon);
								y2 = inysize*(maxlat - currLat)/(maxlat-minlat);
								//IJ.log("lat="+lat[i]+", lon="+lon[i]+", x1="+x1+", y1="+y1);
								Line newline1 = new Line((int)x1,(int)y1,(int)x2,(int)y2);
								newline1.setStroke(new BasicStroke(Thickness));
								newline1.setStrokeColor(col);
								overlay.add(newline1);
							}
						}
						prevLon = currLon;
						prevLat = currLat;
						prevLaunchTime = LaunchTime;
						PointNum++;
					} // loop over latlon rows in file
			    } catch (IOException ex) {
				    ex.printStackTrace();
			    }
    }
    
    void DrawMultiTracks(String TrackListFname)
    {
    	int i;
    	int maxColors = 7;
    	int colInd = 1;
    	int YearlyTracks = 0;
    	
    	Color col;
        Color[] colTable = {Color.red, Color.green, Color.blue, Color.orange, Color.yellow, Color.cyan, Color.black};
    
        IJ.log("ShowMultiTracks called, TrackListFname="+TrackListFname);
        
        if (MultiTrackFiles[SelectedMultiTrackFile].contains("MultiTrack"))
        {
			// Loop over all Tracks defined in files listed in TracksList.txt file
			String rowList;
			try(
				BufferedReader ListReader = new BufferedReader(new FileReader(TrackListFname));
			)
			{
				while ((rowList = ListReader.readLine()) != null) 
				{
					col = colTable[colInd];
					colInd++;
					if (colInd>=maxColors)
						colInd = 0;
				
					ReadTrackFile(rowList,YearlyTracks,col);
				} // loop over files
			} catch (IOException ex) {
				ex.printStackTrace();
			}
        }
        else // single yearly tracks file specified
        	ReadTrackFile(MultiTrackFiles[SelectedMultiTrackFile],1,Color.yellow);
/* 
        // Code for outputting gif screenshots, if needed to generate an animaed gif...
        String savFile = "D:\\CGI\\P2PHAPS\\Tracks\\20191109_Run620\\testimage2.gif";
        ImagePlus SourceImage = WindowManager.getCurrentImage();
        ImagePlus imp2 = SourceImage.flatten();
        imp2.setTitle(WindowManager.getUniqueName(SourceImage.getTitle()));
        imp2.show();
        ImagePlus imp3 = WindowManager.getCurrentImage();
        new FileSaver(imp3).saveAsGif(savFile);   
*/
    }


    void ReadLLfile(String fname)
    {
    	int NumPts = 0;
    	int iline = 0;
    	String row;
    	try(
    	    BufferedReader csvReader = new BufferedReader(new FileReader(fname));
    	)
    	{
			while ((row = csvReader.readLine()) != null) 
			{
				String[] data = row.split(",");
				// do something with the data
				//IJ.log("line="+iline+", Lat="+data[0]+", Lon="+data[1]);
				geolat[iline] = Float.parseFloat(data[0]);				
				geolon[iline] = Float.parseFloat(data[1]);				
    		    //IJ.log("lat="+lat[iline]+", lon="+lon[iline]+", data[0]="+data[0]);
				iline++;
			}
			csvReader.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }   
        //IJ.log("iline="+iline);
        NumGeodesic = iline-1;
    }
    
    void PlotGridField(int iheight, int imTimeStep, Color col)
    {
    	int ix,iy;
    	int nxy = 10;
    	int nx = nxy;
    	int ny = nxy;
    	double rx,ry,dxy,dx,dy;
    	double midx = inxsize/2;
    	double midy = inysize/2;
    	int NumPts = 0;    	
    	nxy = PlotGrid;
        // For N Atlantic, -105 degs to +15 degs = 120 degs EW, 30 degs to 90 degs n = 60 degs NS
        // Corresponds to 48 x 24 values from winds datasets
        if (TestGrib2)
        {
        	nx = MetNumX/PlotGrid;
        	ny = MetNumY/PlotGrid;
        }
        else
        {
           nx = 48/PlotGrid;
           ny = 24/PlotGrid;
        }
    	dx = (double)inxsize/nx;
    	dy = (double)inysize/ny;
    	//IJ.log("PlotGridField: nx="+nx+", ny="+ny+", dx="+dx+", dy="+dy+", MetNumX="+MetNumX+", MetNumY="+MetNumY+", MetNumT="+MetNumT+", MetNumH="+MetNumH);
    	int ind;
    	int i = 0;
    	int j = 0;    	
    	ind = i*PlotGrid + MetNumX*j*PlotGrid + MetNumX*MetNumY*(iheight*(int)MetNumImages + imTimeStep);
    	//IJ.log("ind="+ind+", imTimeStep="+imTimeStep+", iheight="+iheight+", allFloatsU[ind]="+allFloatsU[ind]+", allFloatsV[ind]="+allFloatsV[ind]);
    	
    	for (j=0; j<ny; j++)
    	{
           ry = dy/2 + j*dy - dy/2;
           if (TestGrib2) ry += dy/2;
    	   for (i=0; i<nx; i++)
           {
              rx = dx/2 + i*dx - dx/2;
              if (TestGrib2) rx += dx/2;
              double xval; // = 20+10*Math.sin(i*2*3.1415926/nx);
              double yval; // = 10+5*Math.cos(j*2*3.1415926/ny);
              int jj = j; //72-j; 
              
              if (TestGrib2) // u/v winds from binary float file derived from Grib2 inputs (0.25deg, 0.5deg, 1.0deg, 2.5 deg)
              	  ind = i*PlotGrid + MetNumX*j*PlotGrid + MetNumX*MetNumY*(iheight*(int)MetNumImages + imTimeStep);
              else if (MetNumT<=12) // Monthly (2.5deg)
              	  ind =  30 + i + MetNumX*jj + MetNumX*MetNumY*(iheight + MetNumH*imTimeStep);
              else if (MetNumT<=366) // Daily (2.5deg)
                  ind =  30 + i + MetNumX*(MetNumT*MetNumH*jj + iheight + MetNumH*imTimeStep);
              else // 6-Hourly (2.5deg)
                  ind =  30 + i + MetNumX*(MetNumT*MetNumH*jj + iheight + MetNumH*imTimeStep);

              if ( (!TestGrib2) && (ind>=fileSizeFloat)) ind = (int)fileSizeFloat-1;
              if (ind>MetNumX*MetNumY*MetNumH*MetNumImages) ind=(int)(MetNumX*MetNumY*MetNumH*MetNumImages)-1;
              xval = allFloatsU[ind];
              yval = -allFloatsV[ind];
              
              DrawArrow(rx, ry, (rx+arrowScale*xval), (ry+arrowScale*yval), col);
           }
        }
           
        Font font = new Font("Arial", Font.PLAIN, 20);
        String Title;
        int MonthDay=iday+1;
        if (TestGrib2) // u/v winds from binary float file derived from Grib2 inputs
            Title = "TimeStep: ("+imTimeStep+") Date: "+Month[imonth]+" "+MonthDay+" Hour: "+ihour+"  Height: "+HeightGrib2Km[iheight]+" km";
        else if (MetNumT<=12) // Monthly
        	Title = "Month: "+Month[imTimeStep]+"  Height: "+HeightKm[iheight]+" km";
	    else if (MetNumT<=366) // Daily
        	Title = "Day-of-Year: ("+imTimeStep+") Date: "+Month[imonth]+" "+MonthDay+"  Height: "+HeightKm[iheight]+" km";
        else // 6-hourly
        	Title = "QuarterDay-of-Year: ("+imTimeStep+") Date: "+Month[imonth]+" "+MonthDay+" "+QtrHour[iQuarterDay]+"  Height: "+HeightKm[iheight]+" km";
        	
        String MetSubStepTitle = "";
        if (DeltaMetSubStep>0)
        	MetSubStepTitle = String.format(" (MetFractionTimeStep = %4.2f)",DeltaMetSubStep);
        Roi textRoi = new TextRoi((int)(inxsize/2)-110, 10, Title+MetSubStepTitle, font);
        textRoi.setStrokeColor(Color.black);  
        overlay.add(textRoi);
    }
       
    void ReadMultipleWindFiles()
    {
    	String row;
    	boolean ThisIsUwind = true;
    	int iline = 0;
    	int TotalFileSize = 0;
    	
    	MetNumImages = 0;
    	
    	try(
    	    BufferedReader csvReader = new BufferedReader(new FileReader(WindFilesList));
    	)
    	{
    		// Need to first ascertain total size!
 			while ((row = csvReader.readLine()) != null) 
			{
				TotalFileSize += new File(DataDir+"/"+row).length()/4/2; // floats
//				TotalFileSize += new File(row).length()/2; // bytes
//				IJ.log("TotalFileSize="+TotalFileSize);
			}
			csvReader.close();
			
    		BufferedReader csvReader2 = new BufferedReader(new FileReader(WindFilesList));
    	    // Loop over pairs of lines (Uwinds followed by V winds), reading each pair in turn
			while ((row = csvReader2.readLine()) != null) 
			{
				//IJ.log("row="+row);
				if (ThisIsUwind)
				{
					FileUfname = DataDir+"/"+row;
					ThisIsUwind = false;
				}
				else
				{
					FileVfname = DataDir+"/"+row;
					IJ.log("FileUfname="+FileUfname+", FileVfname="+FileVfname);
					if (iline==0)
					{
    	                ReadGribWinds(TotalFileSize);
    	                // Get start date...
						// Assume grib2-derived file name of type 20xx-Mmm-U.bin
						int yrpos2 = FileUfname.indexOf("/20")+1;
						String chMonthName = FileUfname.substring(yrpos2+5,yrpos2+7);
						String chMonths="JanFebMarAprMayJunJulAugSepOctNovDec";
						int iMonth = chMonths.indexOf(chMonthName)/3 + 1;
						String chMonthNum = String.format("%02d",iMonth);
						strStartLaunchDate = FileUfname.substring(yrpos2,yrpos2+4)+chMonthNum+"01";
						iStartDoY = CumulDoY[iMonth-1];
						IJ.log("ReadMultipleWindFiles: chMonthName="+chMonthName+", iMonth="+iMonth+", chMonthNum="+chMonthNum+", iStartDoY="+iStartDoY);   	                
    	            }
    	            else
    	            	ReadGribWinds(-1);
    	            ThisIsUwind = true;
    	            iline++;
    	        }
			}			
			csvReader2.close();
			
        } catch (IOException ex) {
            ex.printStackTrace();
        }   
    }
    
    
    public void actionPerformed(ActionEvent e)
    {
    	if (e.getActionCommand().startsWith("Calc Yearly"))
    	{
    		runYearly();
    	}
    	else if (e.getActionCommand().startsWith("Calc MultiLevel"))
    	{
    		runMultiLevel();
    	}
    	else if (e.getActionCommand().startsWith("Calc Connectivity"))
    	{
    		CalcConnectivityStats();
    	}
    	ImagePlus imp = IJ.getImage();	
		imp.setOverlay(overlay);
    }
    
    
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
		 NonBlockingGenericDialog gd = new NonBlockingGenericDialog("Wind Grid");
		 		 
		 gd.setLayout(new GridLayout(16,4,10,0));
		 
		 ReadDefaults();
  	     ReadConfigFile();
  	     
  	     if ((!PlotBalloonsPos) && (!ShowMultiTracks))
  	     {
  	     	 if (WindFilesList.length()>2)
  	     	 {
  	     	 	 ReadMultipleWindFiles(); // assumes these are grib winds!
  	     	 }
  	     	 else
  	     	 {
				 if ( (FileUfname.contains("ugrd")) || (FileUfname.contains("-U")) )
					 ReadGribWinds(0);
				 else
					 ReadWinds();	
             }
		//IJ.log("ShowDialog, MetNumImages="+MetNumImages+", MetNumH="+MetNumH);
			 if (DoExtrapolateHeights)
			    ExtrapolateHeights();
			 //IJ.log("After Height extrapolation, MetNumImages="+MetNumImages+", MetNumH="+MetNumH);		 
  	     }	     
  	     ListSites(SitesDir,"LaunchSites_");
  	     ListSites(SitesDir,"LandingSites_");
 
  	     ListFiles(TracksDir,"Yearly_Details");
	     	 
		int i;
	    IntervalHeight[1]=13; // MM temporary!
	    
		for (i=2;i<(int)MaxTimeHours;i++)
		   IntervalHeight[i] = (double) IntervalHeight[1];
	    
		gd.addSlider("HeightLevel:", 0.0, MetNumH-1, (double)iheight);
		
        if (TestGrib2) // 3-hourly u/v winds from ninary float file derived from Grib2 inputs
        {
           MetTime = MetTimeStep+"hr";  
		   gd.addSlider("Time "+MetTimeStep+"-hourly:", 0.0,  (int)(MetNumImages)-1, (double)imTimeStep);
		}
		else if (MetNumT<=12) // Monthly
		{
			MetTime= "Mon";
		    gd.addSlider("Time Month (0-11):", 0.0, (int)(MetNumImages/MetNumH)-1, (double)imTimeStep);
		}
	    else if (MetNumT<=366) // Daily
	    {
	    	MetTime = "Day";
		    gd.addSlider("Time Day-of-year (0-364):", 0.0,  (int)(MetNumImages/MetNumH)-1, (double)imTimeStep);
		}
	    else //6-hourly
	    {
	    	MetTime = "6hr";
		    gd.addSlider("Time QuarterDay-of-year (0-1459):", 0.0,  (int)(MetNumImages/MetNumH)-1, (double)imTimeStep);
		}
		gd.addNumericField("NumMetSubSteps:", NumMetSubSteps, 0);
		gd.addNumericField("TimeStep (hrs):", TimeStep, 0);
		gd.addNumericField("HeightFraction (0.0-1.0):", HeightFraction, 3);
		String[] ConfigOpts = {"Default","MonthClimate","2018_Daily","2018Daily4x"};
		gd.addNumericField("SplittingInterval (hrs):", SplittingInterval, 0);
		gd.addNumericField("MaxSplitting Intervals:", MaxSplitIntervals, 0);
		gd.addNumericField("MaxSplitHalfRange:", MaxSplitHalfRange, 0);
		gd.addNumericField("EndHeight:", EndHeight, 0);
		gd.addSlider("HeightLevel[1]:", 0.0, 16.0, (double)IntervalHeight[1]);
		gd.addCheckbox("Geodesic Display", ShowGeodesic); gd.addToSameRow();
		gd.addCheckbox("Tracks Display", ShowTracks);
		gd.addCheckbox("Winds Grid Display", ShowGrid); gd.addToSameRow();
		gd.addCheckbox("Only_Good Tracks Display", PlotOnlyGoodTracks);
		gd.addCheckbox("No-Hoper Tracks Display", PlotNoHopers); gd.addToSameRow();
		gd.addCheckbox("Along-Track Winds", AlongTrackWinds);
		//gd.addCheckbox("Calc_Yearly Best Tracks", YearlyBestTracks); gd.addToSameRow();
		//gd.addCheckbox("Calc_MultiLevel Best Tracks", MultiLevelBestTracks);
		gd.addNumericField("LaunchPeriod Duration (days):", LaunchPeriodDuration, 0);
		
		gd.addCheckbox("Any_Track Details Output", OutputAnyTrackDetails); gd.addToSameRow();
		gd.addCheckbox("Best_Track Details Output", OutputBestTrackDetails);
		gd.addCheckbox("UseTimeVarying:", UseTimeVarying);
		gd.addCheckbox("ShowSites", ShowSites);
		gd.addNumericField("Landing Delta lat/lon (degs):", DlatlonThresh, 2);
		gd.addNumericField("MetIntervalMultiplier:",MetIntervalMultiplier,0);
		gd.addChoice("Launch_Site",LaunchChoices,LaunchChoices[SelectedLaunchSite]);
		gd.addChoice("Landing_Site",LandingChoices,LandingChoices[SelectedLandingSite]);
			
		Button bt1 = new Button("Calc Yearly Best Tracks");
		bt1.addActionListener(this);
		gd.add(bt1);
		
		Button bt2 = new Button("Calc MultiLevel Best Tracks");
		bt2.addActionListener(this);
		gd.add(bt2);
		
		Button bt3 = new Button("Calc Connectivity Stats");
		bt3.addActionListener(this);
		gd.add(bt3);
		gd.addMessage("");
			
		//gd.addMessage("--- Process MultiTracks (Balloon positions & Connectivity) ---");
		gd.addMessage("Process MultiTracks");
		gd.addMessage("--------------------");
		gd.addMessage("--------------------");
		gd.addMessage("--------------------");

		gd.addChoice("Select_Tracks",MultiTrackChoices,MultiTrackChoices[SelectedMultiTrackFile]);
		gd.addSlider("FlightTime (hours):", 0.0,  MaxBalloonLaunchTimes*MetTimeStep, BalloonLaunchTime);		
		gd.addCheckbox("ShowMultiTracks from file", ShowMultiTracks); gd.addToSameRow();
		gd.addCheckbox("Balloons Positions Display", PlotBalloonsPos);
		gd.addCheckbox("Visibility Range Display", VisibilityDisplay); gd.addToSameRow();
		gd.addCheckbox("Connectivity Display",ConnectivityDisplay);
		gd.addNumericField("ConnectivityLevel:", ConnectivityLevel, 0);
		//gd.addCheckbox("Calc_Connectivity Stats",ConnectivityStats); gd.addToSameRow();
		gd.addNumericField("UsePrevLaunchGap:",UsePrevLaunchGap,0);
		gd.addNumericField("DialogMaxBalloonTimeSteps:",DialogMaxBalloonTimeSteps,0);
		
        gd.addDialogListener(this);
		gd.showDialog();

		if (gd.wasCanceled()) return DONE;
		IJ.register(this.getClass());       //protect static class variables (filter parameters) from garbage collection    
	//IJ.log("End of ShowDialog");
		return IJ.setupDialog(imp, flags); 
    }
    
    void CalcBestTracksForAllSites(PrintWriter writer, int imTimeStep, int SplitInterval)
    {
		int site;
		int ih,ii;
		int BlackTrackInd;
		boolean PlottedBlackTrack = false;
		
		for (site=0;site<NumLaunchSites;site++)
		{
			double minDlat = 90.0;
			prevLat[SplitInterval] = 90.0;
			int LevAtMinDlat = -1;
			int LevAtAboveBelow = -1;
			double fracLat = 0.0;
			int foundAboveBelow = 0;
			double prevLon = -180.0;
			
			foundAboveBelow = 0;
			DlatAtMinDlat = TargetEndLat;
			BestEndTime = 0.0;
			TimeAtMinDlat = 0.0;
			BlackTrackInd = 0;
				
				for (ih=StartHeight;ih<EndHeight;ih++)
				{
			       PlottedBlackTrack = false;
				   
				   if (ih==StartHeight)
				   	   prevLat[SplitInterval] = 90.0;
				   else
				   	   prevLat[SplitInterval] = EndLat[site];
				   EndLat[site] = 90.0;
				   EndTime[site] = 0.0;
				   if (SplitInterval>=0) 
				   {
				   	   IntervalHeight[SplitInterval] = ih;
				   	   for (ii=SplitInterval+1;ii<MaxTimeSteps;ii++)
				   	   	   IntervalHeight[ii] = ih;
				   }
				   //IJ.log("CalcBestTracksForAllSites: ih0="+IntervalHeight[0]+", ih="+ih+", IntervalHeight["+SplitInterval+"]="+IntervalHeight[SplitInterval]);
			       PlotTracks(writer,Color.orange,false,site,imTimeStep,LaunchLat[site],LaunchLon[site],TimeStep,InterpMode,ih,0.0,0);

                   if (AbsDlat<minDlat)
                   {
			       	   minDlat=AbsDlat;
			       	   LevAtMinDlat = ih;
			       	   TimeAtMinDlat = EndTime[site];
                   }
//			       IJ.log("YYY prevLat["+SplitInterval+"]="+prevLat[SplitInterval]+", EndLat[site]="+EndLat[site]+", EndLon[site]="+EndLon[site]+", targetendLat="+TargetEndLat+", EndTime[site]="+EndTime[site]+", Dlat="+Dlat);

			       double LatChange = EndLat[site] - prevLat[SplitInterval];
			       if (LatChange<0) LatChange=-LatChange;
			       
					boolean LonCheck = false;			
					if (EastToWest)
					{
						if ((EndLon[site]<TargetEndLon+DlatlonThresh) && (prevLon<TargetEndLon+DlatlonThresh)) LonCheck = true;
					}
					else
					{
						if ((EndLon[site]>TargetEndLon-DlatlonThresh) && (prevLon>TargetEndLon-DlatlonThresh)) LonCheck = true;
					}
					
					boolean LonCheck2 = false;			
					if (EastToWest)
					{
						if (EndLon[site]<TargetEndLon+DlatlonThresh) LonCheck2 = true;
					}
					else
					{
						if (EndLon[site]>TargetEndLon-DlatlonThresh) LonCheck2 = true;
					}
					
//			       IJ.log("EndLon[]="+EndLon[site]+"LonCheck="+LonCheck+", LonCheck2="+LonCheck2+", ih="+ih);
			       
                   if ( (LatChange<20.0) && (ih>StartHeight) && (prevLat[SplitInterval] > 0.0) && (EndLat[site]>0) && (EndTime[site]>0) && 
                   	    (LonCheck) &&
                      ( ((prevLat[SplitInterval]>TargetEndLat) && (EndLat[site]<=TargetEndLat)) || ((prevLat[SplitInterval]<=TargetEndLat) && (EndLat[site]>TargetEndLat))) )
			       {
			       	   LevAtAboveBelow = ih-1;
			       	   fracLat = (TargetEndLat - prevLat[SplitInterval])/(newEndLat - prevLat[SplitInterval]);
			       	   BestEndTime = EndTime[site];
			       	   foundAboveBelow++;
			       	   double OrigOtherEndLat = EndLat[site];
//			       	   IJ.log("XXX h[0]="+IntervalHeight[0]+",h[1]="+IntervalHeight[1]+",newEndLat="+newEndLat+", prevLat["+SplitInterval+"]="+prevLat[SplitInterval]+", EndLat[site]="+EndLat[site]+", EndTime[site]="+EndTime[site]+", fracLat="+fracLat+", Dlat="+Dlat);
			       	   int EndInterval = (int)(EndTime[site]/SplittingInterval-0.5);
			       	   int savHeight=(int)IntervalHeight[EndInterval];
			       	   IntervalHeight[EndInterval] = ih-1;
				   	      for (ii=SplitInterval;ii<MaxTimeSteps;ii++)
				   	   	      IntervalHeight[ii] = ih-1;
				   	   
			           PlotTracks(writer,Color.blue,true,site,imTimeStep,LaunchLat[site],LaunchLon[site],TimeStep,InterpMode,ih-1,fracLat,EndInterval);
			           
			           // May need to iterate to get convergence due to non-linear mapping between height level and fracLat			           
			           if (AbsDlat>DlatlonThresh)
			           {			       	       
			       	       double xref = 0.0;
			       	       double yref = prevLat[SplitInterval];
			       	       if ((newEndLat-yref)/(OrigOtherEndLat-yref) < 0.5)
			       	       {
			       	       	   yref = OrigOtherEndLat;
			       	       	   xref = 1.0;
			       	       }
			       	       double newYfrac = (TargetEndLat - yref) / ( newEndLat - yref);
			       	       fracLat = xref + newYfrac*(fracLat - xref);
			       	       
//			       	       IJ.log("YYYYb: xref="+xref+", yref="+yref+", newYrac="+newYfrac+",prevLat[]="+prevLat[SplitInterval]+", OrigOtherEndLat="+OrigOtherEndLat+",  TargetEndLat="+TargetEndLat);
//			       	       IJ.log("YYYY ih="+ih+",newEndLat="+newEndLat+", prevLat["+SplitInterval+"]="+prevLat[SplitInterval]+", EndLat[site]="+EndLat[site]+", EndTime[site]="+EndTime[site]+", fracLat="+fracLat+", Dlat="+Dlat);
			       	       
			       	       PlotTracks(writer,Color.blue,true,site,imTimeStep,LaunchLat[site],LaunchLon[site],TimeStep,InterpMode,ih-1,fracLat,EndInterval);
			       	       
			               if (AbsDlat>DlatlonThresh) 
			               {
			       	           xref = 0.0;
			       	           yref = prevLat[SplitInterval];
			       	           if ((newEndLat-yref)/(OrigOtherEndLat-yref) < 0.5)
			       	           {
			       	       	       yref = OrigOtherEndLat;
			       	       	       xref = 1.0;
			       	           }
			       	           newYfrac = (TargetEndLat - yref) / ( newEndLat - yref);
			       	           fracLat = xref + newYfrac*(fracLat - xref);

//			       	           IJ.log("ZZZ ih="+ih+",newEndLat="+newEndLat+", prevLat["+SplitInterval+"]="+prevLat[SplitInterval]+", EndLat[site]="+EndLat[site]+", EndTime[site]="+EndTime[site]+", fracLat="+fracLat+", Dlat="+Dlat);			       	       
			       	           PlotTracks(writer,Color.blue,true,site,imTimeStep,LaunchLat[site],LaunchLon[site],TimeStep,InterpMode,ih-1,fracLat,EndInterval);
			       	       
//			       	           IJ.log("ZZZb ih="+ih+",newEndLat="+newEndLat+", prevLat["+SplitInterval+"]="+prevLat[SplitInterval]+", EndLat[site]="+EndLat[site]+", EndTime[site]="+EndTime[site]+", fracLat="+fracLat+", Dlat="+Dlat);	
			                   if (AbsDlat<DlatlonThresh) 
			                   {
			               	       PlotTracks(writer,Color.black,true,site,imTimeStep,LaunchLat[site],LaunchLon[site],TimeStep,InterpMode,ih-1,fracLat,EndInterval);
			               	       BlackTrackInd++;
			               	       PlottedBlackTrack = true;
			       	           }
			               }
			               else if (LonCheck2)
			               {
			               	   PlotTracks(writer,Color.black,true,site,imTimeStep,LaunchLat[site],LaunchLon[site],TimeStep,InterpMode,ih-1,fracLat,EndInterval);
			               	   BlackTrackInd++;
			               	   PlottedBlackTrack = true;
			               }
			           }
			           else if (LonCheck2)
			           {
			           	   PlotTracks(writer,Color.black,true,site,imTimeStep,LaunchLat[site],LaunchLon[site],TimeStep,InterpMode,ih-1,fracLat,EndInterval);
			           	   BlackTrackInd++;
			           	   PlottedBlackTrack = true;
			           }

			           if (PlottedBlackTrack)
			           {
        	               // Save Track info to enable re-plotting of best track for this launch date/time
        	               String outputLine = String.format("Track %3d, frac=%6.3f, Ht0=%2d, Ht1=%2d, Ht2=%2d, Ht3=%2d, GeoSq=%8.1f",BlackTrackInd,fracLat,(int)IntervalHeight[0],(int)IntervalHeight[1],(int)IntervalHeight[2],(int)IntervalHeight[3],GeodesicSumSq);
                           //IJ.log("Black Track "+outputLine);
                           BestGeodSumSq[NumBestGeod] = GeodesicSumSq;
                           BestGeodNumIntervals[NumBestGeod] = CurrNumSplitIntervals;
                           BestGeodFracLat[NumBestGeod] = fracLat;
                           BestGeodHt0[NumBestGeod] = (int)IntervalHeight[0];
                           BestGeodHt1[NumBestGeod] = (int)IntervalHeight[1];
                           BestGeodHt2[NumBestGeod] = (int)IntervalHeight[2];
                           BestGeodHt3[NumBestGeod] = (int)IntervalHeight[3];                          
                           BestGeodHt4[NumBestGeod] = (int)IntervalHeight[4];
                           BestGeodHt5[NumBestGeod] = (int)IntervalHeight[5];                          
			           	   NumBestGeod++;
			           }
			           // Restore IntervalHeight[]
			           IntervalHeight[EndInterval] = savHeight;
			           if (!YearlyBestTracks)
				   	      for (ii=SplitInterval;ii<MaxTimeSteps;ii++)
				   	   	      IntervalHeight[ii] = ih;
			       }
			       else if (PlotNoHopers)
			       {
			       	   // if requested, plot all no-hopers!
			           PlotTracks(writer,Color.cyan,true,site,imTimeStep,LaunchLat[site],LaunchLon[site],TimeStep,InterpMode,ih,0.0,0);
			       }
			       prevLon = EndLon[site];
			    }
			    BestLevel = -1;
			    if (foundAboveBelow>0)
			    {
			    	BestLevel = LevAtAboveBelow;
			    	DlatAtMinDlat = 0.0;
			    }
			    else
			    {
			    	BestLevel = LevAtMinDlat;
			    	BestEndTime = TimeAtMinDlat;
			    }
			    
			    ShowTracks = true;
			    //IJ.log("Best height for site "+site+" is "+LevAtMinDlat+", Dlat="+minDlat+", fracLat="+fracLat);
			    if (SplitInterval>=0) 
			    {
			    	IntervalHeight[SplitInterval] = BestLevel;
				   for (ii=SplitInterval+1;ii<MaxTimeSteps;ii++)
				   	   IntervalHeight[ii] = BestLevel;
			    }
			    int EndInterval = (int)(BestEndTime/SplittingInterval-0.5);
			    
			    double AbsDlat = DlatAtMinDlat;
			    if (AbsDlat<0) AbsDlat=-AbsDlat;			    

			    ShowTracks = false;			
			    
			    BestHeight = BestLevel + fracLat;
//				IJ.log("Time="+imTimeStep+", site="+site+", best height="+BestHeight+", dLat="+DlatAtMinDlat+", EndTime="+EndTime[site]+" hrs");
			}
    }
    
    void OutputRunConfigInfo( String NewRunName)
    {
		 try (

				FileOutputStream outfile = new FileOutputStream(TracksDir+"/"+NewRunName+"/"+NewRunName+"_Config.txt");
				PrintWriter writer = new PrintWriter(outfile);
		 )
		 {

		 	   double LaunchTimeStep = MetTimeStep/NumMetSubSteps;
		 	 	 	 
				writer.println("Launch Site: "+LaunchName[0]);
				writer.println("StartLaunchDate: "+strStartLaunchDate);
				writer.println("StartLaunchTime: "+strStartLaunchTime);
				
				writer.println("FileUfname: "+FileUfname);
				writer.println("FileVfname: "+FileVfname);
				writer.println("MetCellSize: "+MetCellSize);
				
				writer.println("LaunchTimeStep: "+LaunchTimeStep);
				writer.println("MaxLaunchTimes: "+MaxLaunchTimes);
				writer.println("SplittingInterval: "+SplittingInterval);
				writer.println("MaxSplitIntervals: "+MaxSplitIntervals);
				writer.println("MaxSplitHalfRange: "+MaxSplitHalfRange);
				writer.println("MaxBalloonLaunchTimes: "+MaxBalloonLaunchTimes);
				writer.println("MetTimeSteps: "+MetTimeStep);
				writer.println("NumMetSubSteps: "+NumMetSubSteps);
				writer.println("TimeStep: "+TimeStep);
				writer.println("DlatlonThresh: "+DlatlonThresh);
				writer.println("UseTimeVarying: "+UseTimeVarying);
				writer.println("OutputBestTrackDetails: "+OutputBestTrackDetails);
				writer.println("OutputAnyTrackDetails: "+OutputAnyTrackDetails);
		
				writer.println("MetNumX: "+MetNumX);
				writer.println("MetNumY: "+MetNumY);
				writer.println("MetNumH: "+MetNumH);
				writer.println("MetNumT: "+MetNumT);
				
				writer.println("imTimeStep: "+imTimeStep);
				writer.println("MetTime: "+MetTime);
								
				writer.println("StartLon: "+StartLon);
				writer.println("StartLat: "+StartLat);
				writer.println("Landing Site: "+LandingName[0]);
				writer.println("TargetEndLon: "+TargetEndLon);
				writer.println("TargetEndLat: "+TargetEndLat);
				
				writer.println("Start GroundStation: "+GroundStationName[0]);
				writer.println("End GroundStation: "+GroundStationName[1]);
								
				writer.close();
		 }
		 catch (IOException ex) {
			ex.printStackTrace();
		 }    	
    }
    
    void CalcBestTracksForAllSplitIntervals(PrintWriter writer, PrintWriter binwriter, int imTimeStep, double SplittingInterval, int StartLaunchTime, int MaxLaunchTimes, AtomicInteger progress)
    {
    	int SplitInterval0 = 0;
    	int SplitInterval1 = 1;
    	int SplitInterval2 = 2;
    	int ih0 = 0;
    	int ih1 = 0;
    	int ih2 = 0;
    	int ih3 = 0;
    	int ih4 = 0;
    	int MaxSplitHeights = EndHeight; //MetNumH;
    	int[] ihStart = new int[20];
    	int[] ihEnd = new int[20];
    	String outputLine;
    	
    	ihStart[0] = StartHeight;
    	ihEnd[0] = MaxSplitHeights;
    	    	
//    	if (MaxLaunchTimes>StartLaunchTime+1)
//    	    IJ.log("CalcBestTracksForAllSplitIntervals("+imTimeStep+","+SplittingInterval+"), MaxLaunchTimes="+MaxLaunchTimes);    	   

    	int CountTimesWithValidTracks = 0;
    	int LaunchTime = 0;
		countYellowTracks = 0;
		countBlackTracks = 0;
		countBlueTracks = 0;
		countCyanTracks = 0;

		for (LaunchTime=StartLaunchTime;LaunchTime<MaxLaunchTimes;LaunchTime++)
		{
			countValidTracks = 0;
			NumBestGeod = 0;
			for (int LonBin=0; LonBin<8*4; LonBin++) // NB: Currently hardwired LonBins!
				LonBinCount[LonBin] = 0;
			
			if (!YearlyBestTracks)
			{
			   countYellowTracks = 0;
			   countBlackTracks = 0;
			   countBlueTracks = 0;
			   countCyanTracks = 0;
			}
			double LaunchHours = (double)LaunchTime*MetTimeStep*MetIntervalMultiplier/NumMetSubSteps;
			//IJ.log("LaunchHours = "+LaunchHours);

			// First for one splitting interval (always do this one!)
			CurrNumSplitIntervals = 1;
			CalcBestTracksForAllSites(writer,LaunchTime,0);
			if ((DlatAtMinDlat<DlatlonThresh) && (-DlatAtMinDlat<DlatlonThresh))
			{
			   countValidTracks++;
			   outputLine = String.format("0: LaunchTime=%4d, h0=%2d, h1=%2d, BestH1=%5.2f, dLat=%6.2f, EndTime=%6.2f hr %n",LaunchTime,(int)IntervalHeight[0],(int)IntervalHeight[1],BestHeight,DlatAtMinDlat,BestEndTime);
			   System.out.printf(outputLine);
			   outputLine = String.format("1,%6.1f,%2d,%2d,%2d,%2d,%2d,%2d,%5.2f,%6.2f%n",LaunchHours,(int)IntervalHeight[0],(int)IntervalHeight[1],(int)IntervalHeight[2],(int)IntervalHeight[3],(int)IntervalHeight[3],(int)IntervalHeight[5],DlatAtMinDlat,BestEndTime);
			   if ( (!OutputBestTrackDetails) && (OutputAnyTrackDetails) ) writer.printf(outputLine);
			}
			
			if (MaxSplitIntervals>=2)
			{
			   // Next for 2 splitting intervals
			   CurrNumSplitIntervals = 2;
			   for (ih0=ihStart[0];ih0<ihEnd[0];ih0++)
			   {
				    IntervalHeight[0] = ih0;

					CalcBestTracksForAllSites(writer,LaunchTime,1);
					if ((DlatAtMinDlat<DlatlonThresh) && (-DlatAtMinDlat<DlatlonThresh))
					{
			           countValidTracks++;
					   outputLine = String.format("1: LaunchTime=%4d, h0=%2d, h1=%2d, BestH1=%5.2f, dLat=%6.2f, EndTime=%6.2f hr %n",LaunchTime,(int)IntervalHeight[0],(int)IntervalHeight[1],BestHeight,DlatAtMinDlat,BestEndTime);
					   System.out.printf(outputLine);
					   outputLine = String.format("2,%6.1f,%2d,%2d,%2d,%2d,%2d,%2d,%5.2f,%6.2f%n",LaunchHours,(int)IntervalHeight[0],(int)IntervalHeight[1],(int)IntervalHeight[2],(int)IntervalHeight[3],(int)IntervalHeight[3],(int)IntervalHeight[5],DlatAtMinDlat,BestEndTime);
			           if ((!OutputBestTrackDetails) && (OutputAnyTrackDetails) )  writer.printf(outputLine);
					}
			   }
			}
	
			if (MaxSplitIntervals>=3)
			{
			   // Next for 3 splitting intervals
			   CurrNumSplitIntervals = 3;
			   ihStart[0] = StartHeight;
			   ihEnd[0] = MaxSplitHeights;
			   for (ih0=ihStart[0];ih0<ihEnd[0];ih0++)
			   {
				   IntervalHeight[0] = ih0;
				   ihStart[1] = ih0 - MaxSplitHalfRange;
				   if (ihStart[1]<StartHeight) 
				   	   ihStart[1]=StartHeight;
				   ihEnd[1] = ih0 + MaxSplitHalfRange;
				   if (ihEnd[1]>MaxSplitHeights) 
				   	   ihEnd[1] = MaxSplitHeights;
				   
				   for (ih1=ihStart[1];ih1<ihEnd[1];ih1++)
				   {
					   IntervalHeight[1] = ih1;
					   CalcBestTracksForAllSites(writer,LaunchTime,2);
					   if ((DlatAtMinDlat<DlatlonThresh) && (-DlatAtMinDlat<DlatlonThresh))
					   {
			              countValidTracks++;
						  outputLine = String.format("2: LaunchTime=%4d, h0=%2d, h1=%2d, BestH1=%5.2f, dLat=%6.2f, EndTime=%6.2f hr %n",LaunchTime,(int)IntervalHeight[0],(int)IntervalHeight[1],BestHeight,DlatAtMinDlat,BestEndTime);
						  System.out.printf(outputLine);
						  outputLine = String.format("3,%6.1f,%2d,%2d,%2d,%2d,%2d,%2d,%5.2f,%6.2f%n",LaunchHours,(int)IntervalHeight[0],(int)IntervalHeight[1],(int)IntervalHeight[2],(int)IntervalHeight[3],(int)IntervalHeight[3],(int)IntervalHeight[5],DlatAtMinDlat,BestEndTime);
			              if ( (!OutputBestTrackDetails) && (OutputAnyTrackDetails) ) writer.printf(outputLine);
					   }
				   }                                                                    
			   }
			}
	
			if (MaxSplitIntervals>=4)
			{
			   ihStart[0] = StartHeight;
			   ihEnd[0] = MaxSplitHeights;
			   // Next for 4 splitting intervals
			   CurrNumSplitIntervals = 4;
			   for (ih0=StartHeight;ih0<MetNumH;ih0++)
			   {
				   IntervalHeight[0] = ih0;
				   ihStart[1] = StartHeight;
				   ihEnd[1] = MaxSplitHeights;
				   ihStart[1] = ih0 - MaxSplitHalfRange;
				   if (ihStart[1]<StartHeight) 
				   	   ihStart[1]=StartHeight;
				   ihEnd[1] = ih0 + MaxSplitHalfRange;
				   if (ihEnd[1]>MaxSplitHeights) 
				   	   ihEnd[1] = MaxSplitHeights;

				   for (ih1=ihStart[1];ih1<ihEnd[1];ih1++)
				   {
					   IntervalHeight[1] = ih1;
					   ihStart[2] = StartHeight;
					   ihEnd[2] = MaxSplitHeights;
				       ihStart[2] = ih1 - MaxSplitHalfRange;
				       if (ihStart[2]<StartHeight) 
				   	      ihStart[2]=StartHeight;
				       ihEnd[2] = ih1 + MaxSplitHalfRange;
				       if (ihEnd[2]>MaxSplitHeights) 
				   	      ihEnd[2] = MaxSplitHeights;
				   	  
					   for (ih2=ihStart[2];ih2<ihEnd[2];ih2++)
					   {   		       
						   IntervalHeight[2] = ih2;
						   CalcBestTracksForAllSites(writer,LaunchTime,3);
						   
						   if ((DlatAtMinDlat<DlatlonThresh) && (-DlatAtMinDlat<DlatlonThresh))
						   {
			                  countValidTracks++;
							  outputLine = String.format("3: LaunchTime=%4d, h0=%2d, h1=%2d, BestH1=%5.2f, dLat=%6.2f, EndTime=%6.2f hr %n",LaunchTime,(int)IntervalHeight[0],(int)IntervalHeight[1],BestHeight,DlatAtMinDlat,BestEndTime);
							  System.out.printf(outputLine);
							  outputLine = String.format("4,%6.1f,%2d,%2d,%2d,%2d,%2d,%2d,%5.2f,%6.2f%n",LaunchHours,(int)IntervalHeight[0],(int)IntervalHeight[1],(int)IntervalHeight[2],(int)IntervalHeight[3],(int)IntervalHeight[3],(int)IntervalHeight[5],DlatAtMinDlat,BestEndTime);
			                  if ( (!OutputBestTrackDetails) && (OutputAnyTrackDetails) ) writer.printf(outputLine);
						   }
					   }
				   }                                                                    
			   }
			}
			
			if (MaxSplitIntervals>=5)
			{
			   ihStart[0] = StartHeight;
			   ihEnd[0] = MaxSplitHeights;
			   // Next for 5 splitting intervals
			   CurrNumSplitIntervals = 5;			   
			   for (ih0=StartHeight;ih0<MetNumH;ih0++)
			   {
				   IntervalHeight[0] = ih0;
				   ihStart[1] = StartHeight;
				   ihEnd[1] = MaxSplitHeights;
				   ihStart[1] = ih0 - MaxSplitHalfRange;
				   if (ihStart[1]<StartHeight) 
				   	   ihStart[1]=StartHeight;
				   ihEnd[1] = ih0 + MaxSplitHalfRange;
				   if (ihEnd[1]>MaxSplitHeights) 
				   	   ihEnd[1] = MaxSplitHeights;

				   for (ih1=ihStart[1];ih1<ihEnd[1];ih1++)
				   {
					   IntervalHeight[1] = ih1;
					   ihStart[2] = StartHeight;
					   ihEnd[2] = MaxSplitHeights;
				       ihStart[2] = ih1 - MaxSplitHalfRange;
				       if (ihStart[2]<StartHeight) 
				   	      ihStart[2]=StartHeight;
				       ihEnd[2] = ih1 + MaxSplitHalfRange;
				       if (ihEnd[2]>MaxSplitHeights);
				   	      ihEnd[2] = MaxSplitHeights;
				          
					   for (ih2=ihStart[2];ih2<ihEnd[2];ih2++)
					   {   		       
						   IntervalHeight[2] = ih2;
						   ihStart[3] = StartHeight;
						   ihEnd[3] = MaxSplitHeights;
						   ihStart[3] = ih2 - MaxSplitHalfRange;
						   if (ihStart[3]<StartHeight) 
							  ihStart[3]=StartHeight;
						   ihEnd[3] = ih2 + MaxSplitHalfRange;
						   if (ihEnd[3]>MaxSplitHeights);
				   	          ihEnd[3] = MaxSplitHeights;
							   
					      for (ih3=ihStart[3];ih3<ihEnd[3];ih3++)
					      {   		       
						       IntervalHeight[3] = ih3;
							   CalcBestTracksForAllSites(writer,LaunchTime,4);
							   
							   if ((DlatAtMinDlat<DlatlonThresh) && (-DlatAtMinDlat<DlatlonThresh))
							   {
								  countValidTracks++;
								  outputLine = String.format("4: LaunchTime=%4d, h0=%2d, h1=%2d, BestH1=%5.2f, dLat=%6.2f, EndTime=%6.2f hr %n",LaunchTime,(int)IntervalHeight[0],(int)IntervalHeight[1],BestHeight,DlatAtMinDlat,BestEndTime);
								  System.out.printf(outputLine);
								  outputLine = String.format("5,%6.1f,%2d,%2d,%2d,%2d,%2d,%2d,%5.2f,%6.2f%n",LaunchHours,(int)IntervalHeight[0],(int)IntervalHeight[1],(int)IntervalHeight[2],(int)IntervalHeight[3],(int)IntervalHeight[3],(int)IntervalHeight[5],DlatAtMinDlat,BestEndTime);
								  if ( (!OutputBestTrackDetails) && (OutputAnyTrackDetails) ) writer.printf(outputLine);
							   }
						  }
					   }
				   }                                                                    
			   }
			}

			if (MaxSplitIntervals>=6)
			{
			   ihStart[0] = StartHeight;
			   ihEnd[0] = MaxSplitHeights;
			   // Next for 6 splitting intervals
			   CurrNumSplitIntervals = 6;
			   for (ih0=StartHeight;ih0<MetNumH;ih0++)
			   {
				   IntervalHeight[0] = ih0;
				   ihStart[1] = StartHeight;
				   ihEnd[1] = MaxSplitHeights;
				   ihStart[1] = ih0 - MaxSplitHalfRange;
				   if (ihStart[1]<StartHeight) 
				   	   ihStart[1]=StartHeight;
				   ihEnd[1] = ih0 + MaxSplitHalfRange;
				   if (ihEnd[1]>MaxSplitHeights) 
				   	   ihEnd[1] = MaxSplitHeights;

				   for (ih1=ihStart[1];ih1<ihEnd[1];ih1++)
				   {
					   IntervalHeight[1] = ih1;
					   ihStart[2] = StartHeight;
					   ihEnd[2] = MaxSplitHeights;
				       ihStart[2] = ih1 - MaxSplitHalfRange;
				       if (ihStart[2]<StartHeight) 
				   	      ihStart[2]=StartHeight;
				       ihEnd[2] = ih1 + MaxSplitHalfRange;
				       if (ihEnd[2]>MaxSplitHeights)
				   	      ihEnd[2] = MaxSplitHeights;
				          
					   for (ih2=ihStart[2];ih2<ihEnd[2];ih2++)
					   {   		       
						   IntervalHeight[2] = ih2;
						   ihStart[3] = StartHeight;
						   ihEnd[3] = MaxSplitHeights;
						   ihStart[3] = ih2 - MaxSplitHalfRange;
						   if (ihStart[3]<StartHeight) 
							  ihStart[3]=StartHeight;
						   ihEnd[3] = ih2 + MaxSplitHalfRange;
						   if (ihEnd[2]>MaxSplitHeights);
				   	         ihEnd[3] = MaxSplitHeights;
							   
					      for (ih3=ihStart[3];ih3<ihEnd[3];ih3++)
					      {   		       
						      IntervalHeight[3] = ih3;						       
						      ihStart[4] = StartHeight;
						      ihEnd[4] = MaxSplitHeights;
						      ihStart[4] = ih3 - MaxSplitHalfRange;
						      if (ihStart[4]<StartHeight) 
							     ihStart[4]=StartHeight;
						      ihEnd[4] = ih3 + MaxSplitHalfRange;
							  if (ihEnd[4]>MaxSplitHeights);
						       	 ihEnd[4] = MaxSplitHeights;

					          for (ih4=ihStart[4];ih4<ihEnd[4];ih4++)
					          {   		       
						           IntervalHeight[4] = ih4;						       
								   CalcBestTracksForAllSites(writer,LaunchTime,5);
								   
								   if ((DlatAtMinDlat<DlatlonThresh) && (-DlatAtMinDlat<DlatlonThresh))
								   {
									  countValidTracks++;
									  outputLine = String.format("5: LaunchTime=%4d, h0=%2d, h1=%2d, BestH1=%5.2f, dLat=%6.2f, EndTime=%6.2f hr %n",LaunchTime,(int)IntervalHeight[0],(int)IntervalHeight[1],BestHeight,DlatAtMinDlat,BestEndTime);
									  System.out.printf(outputLine);
									  outputLine = String.format("6,%6.1f,%2d,%2d,%2d,%2d,%2d,%2d,%5.2f,%6.2f%n",LaunchHours,(int)IntervalHeight[0],(int)IntervalHeight[1],(int)IntervalHeight[2],(int)IntervalHeight[3],(int)IntervalHeight[3],(int)IntervalHeight[5],DlatAtMinDlat,BestEndTime);
									  if ( (!OutputBestTrackDetails) && (OutputAnyTrackDetails) ) writer.printf(outputLine);
								   }
							  }
						  }
					   }
				   }                                                                    
			   }
			}
			
			if (countValidTracks>0)
			   CountTimesWithValidTracks++;
		   if (DebugTrajectories) IJ.log("LaunchTime="+LaunchTime+", countValidTracks="+countValidTracks+", NumBestGeod="+NumBestGeod);
		   MinSumSq = BestGeodSumSq[0];
		   int BestIndGeod = 0;
		   if (NumBestGeod>0)
		   {
			   for (int indGeod=0;indGeod<NumBestGeod;indGeod++)
			   {
				   if (BestGeodSumSq[indGeod]<MinSumSq)
				   {
					   MinSumSq = BestGeodSumSq[indGeod];
					   BestIndGeod = indGeod;
				   }
				   //IJ.log(indGeod+", sumSq="+BestGeodSumSq[indGeod]+", BestIndGeod="+BestIndGeod);
			   }
			   
			   // Now plot track which reaches launch zone which is closest to geodesic
			   IntervalHeight[0] = BestGeodHt0[BestIndGeod];
			   IntervalHeight[1] = BestGeodHt1[BestIndGeod];
			   IntervalHeight[2] = BestGeodHt2[BestIndGeod];
			   IntervalHeight[3] = BestGeodHt3[BestIndGeod];
			   IntervalHeight[4] = BestGeodHt4[BestIndGeod];
			   IntervalHeight[5] = BestGeodHt5[BestIndGeod];
			   int ih;
			   for (ih=6;ih<MaxTimeSteps;ih++)
			   	   IntervalHeight[ih] = IntervalHeight[5];
			   CurrNumSplitIntervals = BestGeodNumIntervals[NumBestGeod];
			   
			   int isite=0;
			   double fracLat = BestGeodFracLat[BestIndGeod];
			   int EndInterval = (int)(EndTime[isite]/SplittingInterval-0.5);
			   PlotTracks(writer,Color.yellow,true,isite,LaunchTime,LaunchLat[isite],LaunchLon[isite],TimeStep,InterpMode,BestGeodHt0[BestIndGeod],fracLat,EndInterval);
			   outputLine = String.format("%d,%6.1f,%2d,%2d,%2d,%2d,%2d,%2d,%5.2f,%6.2f%n",CurrNumSplitIntervals,LaunchHours,(int)IntervalHeight[0],(int)IntervalHeight[1],(int)IntervalHeight[2],(int)IntervalHeight[3],(int)IntervalHeight[3],(int)IntervalHeight[5],DlatAtMinDlat,BestEndTime);
			   if ( (!OutputBestTrackDetails) && (!OutputAnyTrackDetails) && (YearlyBestTracks) ) writer.printf(outputLine);
    	   }
    	   
    	   // Report LonBinCounts
    	   if (binwriter!=null)
    	   {
			   for (int BinPrio=0;BinPrio<4;BinPrio++)
			   {
				   int bin = 8*BinPrio;
				   //IJ.log(LaunchTime+":"+BinPrio+": "+LonBinCount[bin]+", "+LonBinCount[bin+1]+", "+LonBinCount[bin+2]+", "+LonBinCount[bin+3]+", "+LonBinCount[bin+4]+", "+LonBinCount[bin+5]+", "+LonBinCount[bin+6]+", "+LonBinCount[bin+7]);
				   outputLine = String.format("%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%n",LaunchTime,BinPrio,LonBinCount[bin],LonBinCount[bin+1],LonBinCount[bin+2],LonBinCount[bin+3],LonBinCount[bin+4],LonBinCount[bin+5],LonBinCount[bin+6],LonBinCount[bin+7]);
				   binwriter.printf(outputLine);
			   }
   	       }
    	}
    	
    	IJ.log("NumValid="+countValidTracks+"Cyan="+countCyanTracks+", Blue="+countBlueTracks+", Black="+countBlackTracks+", Yellow="+countYellowTracks);
    	
    	int NumTimes = MaxLaunchTimes - StartLaunchTime;
    	if (NumTimes>1)
    	{
    		double Percent = (double)CountTimesWithValidTracks*100.0/NumTimes;
    		int iPerc = (int) Percent;
    	    IJ.log("TimesWithValidTracks="+CountTimesWithValidTracks+" out of total tracks="+NumTimes+", Percentage="+iPerc+"%");
    	}
    }

    void runYearly()
    {
			final AtomicInteger progress = new AtomicInteger(0);

			ReadLaunchSitesFile(LaunchSitesFname);
//			ReadLandingSitesFile(LandingSitesFname);
 		    RunStartTime = System.currentTimeMillis();

 		    YearlyBestTracks = true;
			MaxLaunchTimes = (int)(NumMetSubSteps*MetNumImages/MetNumH);

    	 	ListDirs(TracksDir);

    	 	NewRunName = LaunchName[0] + "_Run" + NewRunNum;
    	 	new File(TracksDir+"/"+NewRunName).mkdir();
    	 	OutputRunConfigInfo(NewRunName);

			String name = "Run"+NewRunNum+"_Yearly_Summary";
			if (OutputBestTrackDetails)
				name = "Run"+NewRunNum+"_Yearly_Details";
			
			try (
				PrintWriter yearwriter = new PrintWriter(TracksDir+"/"+NewRunName+"/"+LaunchName[0]+"_"+name+"_Met"+MetTime+"_SubStep"+NumMetSubSteps+"_Split"+MaxSplitIntervals+"_"+SplittingInterval+"h.csv", "UTF-8");
				PrintWriter binwriter = new PrintWriter(TracksDir+"/"+NewRunName+"/"+LaunchName[0]+"_"+"Run"+NewRunNum+"_LonBin"+"_Met"+MetTime+"_SubStep"+NumMetSubSteps+"_Split"+MaxSplitIntervals+"_"+SplittingInterval+"h.csv", "UTF-8");
				)
			{
				if (OutputBestTrackDetails)
				   yearwriter.println("LaunchHours,TrackNum,StepTime,Latitude,Longitude,Height,GeodesicRMSE");				
				else					
				   yearwriter.println("NumIntervals,Hours,H0,H1,H2,H3,H4,H5,MinDlat,EndTime");
			    binwriter.println("Time,Type,70W,60W,50W,40W,30W,20W,10W,0W");
			    
			    countYellowTracks = 0;
//                IJ.log("runYearly: imTimeStep="+imTimeStep+", LaunchPeriodStart="+LaunchPeriodStart+", LaunchPeriodEnd="+LaunchPeriodEnd);
			    CalcBestTracksForAllSplitIntervals(yearwriter,binwriter,imTimeStep,SplittingInterval,LaunchPeriodStart,LaunchPeriodEnd,progress);	
			    yearwriter.close();
			}
			catch (IOException ex) {
                ex.printStackTrace();
            }

			YearlyBestTracks = false; // stop s/w from re-running this unless specifically requested!
			RunEndTime = System.currentTimeMillis();
			double RunElapsedTime = (double)(RunEndTime - RunStartTime)/1000.0;
			IJ.log("Elapsed Run Time = "+RunElapsedTime+" seconds");
    } // runYearly
    
    void runMultiLevel()
    {
			int itime;
			
			MultiLevelBestTracks = true;
			
    	 	ListDirs(TracksDir);

    	 	NewRunName = LaunchName[0] + "_Run" + NewRunNum;
    	 	new File(TracksDir+"/"+NewRunName).mkdir();
    	 	OutputRunConfigInfo(NewRunName);

			if (OutputBestTrackDetails)
			{
			try (
				    FileOutputStream outfile = new FileOutputStream(TracksDir+"/"+NewRunName+"/"+LaunchName[0]+"_Run"+NewRunNum+"_BestTracks_"+MetSubStep+"_Met"+MetTime+".csv");
		            PrintWriter writer = new PrintWriter(outfile);
				    FileOutputStream binfile = new FileOutputStream(TracksDir+"/"+NewRunName+"/"+LaunchName[0]+"_Run"+NewRunNum+"_LonBins_"+imTimeStep+"_Met"+MetTime+".csv");
		            PrintWriter binwriter = new PrintWriter(binfile);
				)
				{
					writer.println("LaunchHours,TrackNum,StepTime,Latitude,Longitude,Height,GeodesicRMSE");
					binwriter.println("Time,Type,70W,60W,50W,40W,30W,20W,10W,0W");

					itime = imTimeStep;
					CalcBestTracksForAllSplitIntervals(writer,binwriter,itime,SplittingInterval,imTimeStep,imTimeStep+1,progress);			
					writer.close();
			    }
			    catch (IOException ex) {
                ex.printStackTrace();
                }	
            }
            else if (OutputAnyTrackDetails)
            {
            	 itime = imTimeStep;
			     try (

				        FileOutputStream outfile = new FileOutputStream(TracksDir+"/"+NewRunName+"/"+LaunchName[0]+"_Run"+NewRunNum+"_BestTracksNoDetail_"+imTimeStep+"_Met"+MetTime+".csv");
		                PrintWriter writer = new PrintWriter(outfile);
				        FileOutputStream binfile = new FileOutputStream(TracksDir+"/"+NewRunName+"/"+LaunchName[0]+"_Run"+NewRunNum+"_LonBins_"+imTimeStep+"_Met"+MetTime+".csv");
		                PrintWriter binwriter = new PrintWriter(binfile);
				 )
				 {
				    	writer.println("NumIntervals,LaunchHours,H0,H1,H2,H3,H4,H5,MinDlat,EndTime");
				    	binwriter.println("Time,Type,70W,60W,50W,40W,30W,20W,10W,0W");

            	        CalcBestTracksForAllSplitIntervals(writer,binwriter,itime,SplittingInterval,imTimeStep,imTimeStep+1,progress);
            	    	writer.close();
            	 }
			     catch (IOException ex) {
                    ex.printStackTrace();
                 }
            }
            else
            	CalcBestTracksForAllSplitIntervals(null,null,imTimeStep,SplittingInterval,imTimeStep,imTimeStep+1,progress);
			MultiLevelBestTracks = false; // stop s/w from re-running this unless specifically requested!
	}

	void BuildOnConnectedPath(int iTime) 
	{
		IJ.log("BuildOnConnectedPath called");
		GetBalloonsForTimeStep(iTime);
		
		// ConnectedPath*[] = last fully connected path (with lat/lons at time identified by ConnectedPathTime
		// ConnectList*[] = list of in-flight balloons from current timestep with no attempt at connectivity
		// Need to use same balloons as previous connected path, but replace lat/lon/ht by values for current timestep
		
		int prevind = 0;		
		for (int currind=0; currind<=NumConnectedPath; currind++) // start with balloons from last fully connected path
		{
			float LaunchTime = ConnectedPathLaunchTime[currind];
			if (LaunchTime>0)
			{
				int MatchFound = -1;
				// find in list of current in-flight balloons (if still present)
				for (int ind=0; ind<ConnectInd; ind++)
				{
					if (ConnectListLaunchTime[ind] == LaunchTime)
						MatchFound = ind;									
				}
				int inflightind = MatchFound;
				if (MatchFound>=0)
				{
					ConnectListLaunchTime[currind] = LaunchTime;
					ConnectListLat[currind] = ConnectListLat[inflightind];
					ConnectListLon[currind] = ConnectListLon[inflightind];
					ConnectListHt[currind] = ConnectListHt[inflightind];
					IJ.log("Copied: currind="+currind+", iTime="+iTime+", inflightind="+inflightind+", launch[]="+LaunchTime+", newLon="+ConnectListLon[currind]+", oldLon="+ConnectedPathLaunchTime[currind]);
					currind++;
				}
				else
				{
					IJ.log("*** No match for Launch time = "+LaunchTime+" found in MultiTracks file, so calculate new trajectory...");
					// Need to calculate a new trajectory and (if one ends up within landing zone) add to BalloonList and get lat/lon for current snapshot
					/* NB: double LaunchHours = imTimeStep*MetTimeStep/NumMetSubSteps;
					int imTimeStep = (int) (LaunchTime*NumMetSubSteps/MetTimeStep);
					OutputAnyTrackDetails = false;
					OutputBestTrackDetails = true;
					PlotNoHopers = false;
					PlotOnlyGoodTracks = true;
					AlongTrackWinds = false;
					CalcBestTracksForAllSplitIntervals(newtrackswriter,null,imTimeStep,SplittingInterval,imTimeStep,imTimeStep+1,progress);
					NumNewTracks++;
					NumGoodNewTracks += countYellowTracks;
					IJ.log("CalcBestTracksForAllSplitIntervals called; countYellowTracks="+countYellowTracks);
					// Need to extract lat,lon,height values for each timestep... then add to ConnectList[]
					IJ.log("UNDER ACTIVE DEVELOPMENT");
					//System.exit(0);
					*/
				}
			}
		}
	}
	
    void CalcConnectivityStats()
    {
    		ConnectivityStats = true;
			PlotBalloonsPos = false;
			
			// Need to load best tracks if not alreay done so...
			if ( (!LoadedBestTracks) )
			{
//				ReadLaunchSitesFile(LaunchSitesFname);
//				ReadLandingSitesFile(LandingSitesFname);
				LoadBestTracks(MultiTrackFname);
			    ReadGroundStationsFile(GroundStationsFname);
				LoadedBestTracks = true;
			}
	        IJ.log("MaxBalloonTimeSteps = "+MaxBalloonTimeSteps+", StartBalloonLaunchTime ="+StartBalloonLaunchTime+", IntegTimeStep="+IntegTimeStep);	

 		    RunStartTime = System.currentTimeMillis();

			String FromMutiTrack = MultiTrackChoices[SelectedMultiTrackFile];
			String name = FromMutiTrack+"_Summary_Connectivity"+ConnectivityLevel+"Stats.csv";
			String detailsname = FromMutiTrack+"_Details_Connectivity"+ConnectivityLevel+"Stats.csv";
			
			// Also need Detailed output = for each timestep, info on all nodes: lat/lon/Height,LaunchTime,"real"or"simulated",adjacent connectivity			
			try (
					   PrintWriter connectwriter = new PrintWriter(TracksDir+"/"+FromMutiTrack+"/"+name, "UTF-8");
					   PrintWriter connectdetailswriter = new PrintWriter(TracksDir+"/"+FromMutiTrack+"/"+detailsname, "UTF-8");
					   PrintWriter newtrackswriter = new PrintWriter(TracksDir+"/"+FromMutiTrack+"/"+FromMutiTrack+"_NewTracks.csv", "UTF-8");
				)
			    {
				connectwriter.println("Hour,PathLength,NumberNodes");		
				connectdetailswriter.println("Hour,BallonNum,LaunchTime,Lat,Lon,Height,PathLength");		
				newtrackswriter.println("LaunchHours,TrackNum,StepTime,Latitude,Longitude,Height,GeodesicRMSE");
				
				NumNewTracks = 0;
				NumGoodNewTracks = 0;
				ConnectCount = 0;
				// loop over launch dates 
				for (int BalloonTimeStep=0;BalloonTimeStep<MaxBalloonTimeSteps; BalloonTimeStep++)
				{
					int iTime = (int) StartBalloonLaunchTime + (int)(BalloonTimeStep*IntegTimeStep);
//					IJ.log("CalcConnectivityStats: iTime="+iTime);

					if (NumConnectedPath>0)
					{
						// We had full connectivity at the last timestep, so try and re-use those balloons
						// First need to get lat/lon/ht for all previous balloons at new timestep...						
						BuildOnConnectedPath(iTime);
						NumConnectedPath = 0;
					}
					else
					{
						CalcPaths(iTime,connectwriter, newtrackswriter);
				    }
				    
//				    output connections if path is connected
					if ((NumBad==0) && (NumGood>2))
					{
						for (int ind=0;ind<=ConnectInd;ind++)
						{
							String outputLine = String.format("%4d,%3d,%6.1f,%8.3f,%8.3f,%8.3f,%8.3f",iTime,ind,ConnectListLaunchTime[ind],ConnectListLat[ind],ConnectListLon[ind],ConnectListHt[ind],ConnectListLength[ind] );
							connectdetailswriter.println(outputLine);
						}

					    // Save this as a ConnectedPath
					    NumConnectedPath = ConnectInd;
					    ConnectedPathTime = iTime;
					    for (int ind=0;ind<=ConnectInd;ind++)
					    {	
						    ConnectedPathLat[ind] = ConnectListLat[ind];
						    ConnectedPathLon[ind] = ConnectListLon[ind];
						    ConnectedPathHt[ind] = ConnectListHt[ind];
						    ConnectedPathLaunchTime[ind] = ConnectListLaunchTime[ind];
					    }
						
//				        save connections if path is connected
						prevConnectInd = ConnectInd;
						for (int ind=0;ind<=ConnectInd;ind++)
						{
							prevConnectListLat[ind] = ConnectListLat[ind];
							prevConnectListLon[ind] = ConnectListLon[ind];
							prevConnectListHt[ind] = ConnectListHt[ind];
							prevConnectListLaunchTime[ind] = ConnectListLaunchTime[ind];    
							prevConnectListLength[ind] = ConnectListLength[ind];
							//if (iTime==240)	IJ.log("iTime="+iTime+", ind="+ind+", setting: prevLaunch[]="+prevConnectListLaunchTime[ind]+", prevConnectListLon[]="+prevConnectListLon[ind]);
						}			                				
					}
                } // loop over timesteps
                
                float fPerc = (float)(ConnectCount)/MaxBalloonTimeSteps;
                int percCount = (int) fPerc;
                IJ.log("NumNewTracks="+NumNewTracks+", NumGoodNewTracks="+NumGoodNewTracks+", ConnectCount="+ConnectCount+" ( "+percCount+"% )");

			    connectdetailswriter.close();
			    connectwriter.close();
			}
			catch (IOException ex) {
                ex.printStackTrace();
            }

			ConnectivityStats = false; // stop s/w from re-running this unless specifically requested!
			RunEndTime = System.currentTimeMillis();
			double RunElapsedTime = (double)(RunEndTime - RunStartTime)/1000.0;
			IJ.log("Elapsed Run Time = "+RunElapsedTime+" seconds");
     }
    
    double GeodesicDistance(float Lat1, float Lon1, float Lat2, float Lon2)
    {
       double phi1 = Lat1*DegToRad; 
       double lamda1=Lon1*DegToRad;
       double phi2 = Lat2*DegToRad; 
       double lamda2=Lon2*DegToRad;
       double dphi = phi2-phi1; 
       double dlamda = lamda2-lamda1;
   
       double a = Math.sin(dphi/2.0)*Math.sin(dphi/2.0) + Math.cos(phi1)*Math.cos(phi2)*Math.sin(dlamda/2.0)*Math.sin(dlamda/2.0);
       double c = 2.0*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
       double d = EarthRadius*c; 
       return d;
    }
    
    double VisibilityRange(double BalloonHeight, double TargetHeight)
    {
    	double h1 = BalloonHeight + EarthRadius;
   	    double h2 = TargetHeight + EarthRadius;
   	    double ReSq = EarthRadius*EarthRadius;
   	    double r1 = Math.sqrt(h1*h1 - ReSq);
   	    double r2 = Math.sqrt(h2*h2 - ReSq);
    	double range = r1 + r2;
    	return range;
    }
    
    boolean CheckVisibility(int ind1, int ind2)
    {
    	boolean isVisible = false;
    	
    	if ((ind1>=0) && (ind2>=0))
    	{
			double range = VisibilityRange(ConnectListHt[ind1],ConnectListHt[ind2]);
			double rangeLat = RadToDeg*(range/EarthRadius);
			double rangeLon = rangeLat/Math.cos(ConnectListLat[ind1]*DegToRad);
			double dLat = ConnectListLat[ind2] - ConnectListLat[ind1];
			double dLon = ConnectListLon[ind2] - ConnectListLon[ind1];
			double dLatLonSq=(dLat*dLat+dLon*dLon);
			dRangeSq=(rangeLat*rangeLat + rangeLon*rangeLon);
			if ( dLatLonSq < dRangeSq )
				isVisible = true;
		}
		else
			IJ.log("CheckVisibility: Requested index out of range - ind1="+ind1+", ind2="+ind2);
		return isVisible;
    }
    
    
	void PlotLocation(float Lat, float Lon, float Height, float Height2, float fLaunch, Color colour)
	{
		double x1 = inxsize*(Lon - minlon)/(maxlon-minlon);
		double y1 = inysize*(maxlat - Lat)/(maxlat-minlat);
						
		OvalRoi circle = new OvalRoi(x1-size/2,y1-size/2,size,size);
		circle.setStroke(new BasicStroke(2));
		circle.setStrokeColor(colour);
		overlay.add(circle);

		int LaunchTime = (int) fLaunch;
		String label = ""+LaunchTime;
		Font font = new Font("Arial", Font.PLAIN, 10);
		Roi textRoi = new TextRoi(x1, y1, label, font);
		textRoi.setStrokeColor(Color.black);  
		overlay.add(textRoi);

		if (VisibilityDisplay)  // show extent of visibility for min BalloonHeight (eg 15 kms)
		{
			double range = VisibilityRange(Height,Height2);
			double rangeDeg = RadToDeg*(range/EarthRadius);
			double rangeLat = inysize*rangeDeg/(maxlat-minlat);
			double rangeLon = inxsize*rangeDeg/Math.cos(Lat*DegToRad)/(maxlon-minlon);
			if (DebugBalloons) IJ.log("Height="+Height+", range="+range+", rLat="+rangeLat+", rLon="+rangeLon);
			OvalRoi visEllipse = new OvalRoi(x1-rangeLon,y1-rangeLat,rangeLon*2,rangeLat*2);
			visEllipse.setStroke(new BasicStroke(2));
			visEllipse.setStrokeColor(Color.yellow);
			overlay.add(visEllipse);
		}
    }

	void InsertNewConnectionLocation(int ind0, float NewLaunchTime, float NewLat, float NewLon, float NewHeight)
	{
		int ind = 0;
		int i=0;
		int ind1 = ind0+1;
		if (DebugConnectivity)
		   IJ.log("InsertNewConnectionLocation: from "+ConnectInd+" down to "+ind1);
		
		// First check whether we already have a launch date matching the new one - if so then delete that => we need an alternative "good" track from that launch date!!!
		for (ind=0; ind<=ConnectInd; ind++)
		{
			if (ConnectListLaunchTime[ind]==NewLaunchTime)
			{
				if (DebugConnectivity)
					IJ.log("Previous trajectory at ind="+ind+" found with LaunchTime="+NewLaunchTime);
				for (i=ind;i<ConnectInd; i++)
				{
					ConnectListLaunchTime[i] = ConnectListLaunchTime[i+1];
					ConnectListLon[i] = ConnectListLon[i+1];
					ConnectListLat[i] = ConnectListLat[i+1];
					ConnectListHt[i] = ConnectListHt[i+1];
				}
				ConnectInd--;
			}
		}		

		for (ind=ConnectInd+1; ind>ind1; ind--)
		{
			ConnectListLaunchTime[ind] = ConnectListLaunchTime[ind-1];
			ConnectListLon[ind] = ConnectListLon[ind-1];
			ConnectListLat[ind] = ConnectListLat[ind-1];
			ConnectListHt[ind] = ConnectListHt[ind-1];
		}
		ind = ind1;
		ConnectListLaunchTime[ind] = NewLaunchTime;
		ConnectListLon[ind] = NewLon;
		ConnectListLat[ind] = NewLat;
		ConnectListHt[ind] = NewHeight;
		ConnectInd++;
	}

	void RemoveBalloons(int from, int to)
	{
		int shift = to - from + 1;
		int NewConnected = ConnectInd - shift;	
		
		for (int i=from; i<=ConnectInd; i++)
		{
			ConnectListLaunchTime[i] = ConnectListLaunchTime[i+shift];
			ConnectListLon[i] = ConnectListLon[i+shift];
			ConnectListLat[i] = ConnectListLat[i+shift];
			ConnectListHt[i] = ConnectListHt[i+shift];
		}
		if (DebugConnectivity)
           IJ.log("RemoveBalloons: from="+from+", to="+to+", shift="+shift+", OldNum="+ConnectInd+", NewNum="+NewConnected);
		ConnectInd = NewConnected;	
	}
	
    void RemoveShortLinks(int ind0)
    {
    	double xnext,ynext;
    	int nextind = 0;
    	int NodesRemoved = 0;
    	int NodesAdded = 0;
    	
    	if (DebugConnectivity)
    		IJ.log("RemoveShortLinks called, IterConnectLevel="+IterConnectLevel+", ConnectivityLevel="+ConnectivityLevel+", ConnectInd="+ConnectInd);
    	
    	for (int ind=ind0;ind<ConnectInd+1;ind++)
    	{
//            IJ.log("Connect_ind="+ind+", level="+ConnectivityLevel+", LaunchTime="+ConnectListLaunchTime[ind]+", Lon="+ConnectListLon[ind]+", Lat="+ConnectListLat[ind]);
            boolean isVisible = false;
		    double xcurr = inxsize*(ConnectListLon[ind]-minlon)/(maxlon-minlon);
		    double ycurr = inysize*(maxlat - ConnectListLat[ind])/(maxlat-minlat);
		    nextind=ind+1;
			xnext = inxsize*(ConnectListLon[ind+1]-minlon)/(maxlon-minlon);
			ynext = inysize*(maxlat - ConnectListLat[ind+1])/(maxlat-minlat);
		    
    		for (int i=1; (ind+i)<ConnectInd; i++ )
    		{
    		   boolean isThisVisible = CheckVisibility(ind, ind+i);
    		   if (isThisVisible)
    		   {
			      xnext = inxsize*(ConnectListLon[ind+i]-minlon)/(maxlon-minlon);
			      ynext = inysize*(maxlat - ConnectListLat[ind+i])/(maxlat-minlat);
			      isVisible = true;
			      nextind = ind+i;
			   }
			}	
			if (nextind!=ind+1) NodesRemoved++;
			
			if ( (IterConnectLevel>=3) && (IterConnectLevel<=ConnectivityLevel) && (nextind<=ConnectInd) )
			{
				if (DebugConnectivity)
					IJ.log(IterConnectLevel+": ind="+ind+" ("+ConnectListLaunchTime[ind]+"), nextind="+nextind+" ("+ConnectListLaunchTime[nextind]+"), isVisible="+isVisible);
				
				if ((IterConnectLevel==ConnectivityLevel) && (!ConnectivityStats))
				 	 PlotLocation(ConnectListLat[ind], ConnectListLon[ind], ConnectListHt[ind], ConnectListHt[nextind], ConnectListLaunchTime[ind], Color.red);
				
				Line newline1 = new Line(xcurr,ycurr,xnext,ynext);
				if (nextind>ind+1)
				{
				   newline1.setStrokeColor(Color.red);
				   if ((ConnectivityLevel>2) && (!ConnectivityStats)) overlay.add(newline1);	
				   RemoveBalloons(ind+1,nextind-1);
				}
				else if ( (nextind==ind+1) && (CheckVisibility(ind, ind+1)) )
				{
				   newline1.setStrokeColor(Color.red);
				   if ((ConnectivityLevel>2) && (!ConnectivityStats)) overlay.add(newline1);	
				}
				else if (IterConnectLevel>=4)
				{
//				   IJ.log("lev="+ConnectivityLevel+"("+IterConnectLevel+"): cyan line between xcurr="+xcurr+", and xnext= "+xnext);
				   
				   // Gap which need filling
				   if (DebugConnectivity)
				   	   IJ.log(IterConnectLevel+": Need to fill gap between Launch="+ConnectListLaunchTime[ind]+" and "+ConnectListLaunchTime[nextind]);
				   
//				   if ((ConnectListLaunchTime[ind]>0) && (ConnectListLaunchTime[nextind]>0))
//				   if ( (ConnectListLaunchTime[ind]>0) || (ConnectListLaunchTime[nextind]>0) )

//				   if ( (NodesAdded==0) && ( (ConnectListLaunchTime[nextind]>0) || (nextind==(ConnectInd-1)) ) )
// NB: The following has configurable time interval threshold (UsePrevLaunchGap, default= 6-hour limit) between launches so that fill-in only attempted if adjacent launches
// STILL Need to consider what to do about fill-ins at launch and landing sites?

                   boolean LinkToLaunch = (nextind==ConnectInd);
                   boolean LinkToLanding = (ind==0);
                   int GapThresh = UsePrevLaunchGap;
                   if (UsePrevLaunchGap<=0) GapThresh=9999; // ie always allow fill-in
                   
				   if ( (NodesAdded==0) && ( ((ConnectListLaunchTime[nextind]>0) && (ConnectListLaunchTime[nextind]<=ConnectListLaunchTime[ind]+GapThresh))|| LinkToLaunch || LinkToLanding) )
				   {
				   	  IJ.log("NodesAdded = "+NodesAdded+", IterConnectLevel="+IterConnectLevel);
				   	  NodesAdded++;
				   	  //IterConnectLevel++;
				      float NewLaunchTime = (ConnectListLaunchTime[ind] + ConnectListLaunchTime[nextind])/2;
//				      IJ.log("Run model with LaunchTime="+NewLaunchTime);
				      
				      // xxx will eventually need to replace with full trajectory modelling for NewLaunchTime!!!
				      float NewLat = (ConnectListLat[ind] + ConnectListLat[nextind])/2;
				      float NewLon = (ConnectListLon[ind] + ConnectListLon[nextind])/2;
				      float NewHeight = (ConnectListHt[ind] + ConnectListHt[nextind])/2;
				      
				      if (ConnectListHt[ind]==0) NewHeight = ConnectListHt[nextind];
				      else if (ConnectListHt[nextind]==0) NewHeight = ConnectListHt[ind];
				      
				      IJ.log("Inserting between "+ind+" and "+nextind+", NewLon="+NewLon+", NewHeight="+NewHeight+": "+ConnectListHt[ind]+", "+ConnectListHt[nextind]);
				      // Insert new location in "ConnectList", and re-display
				      InsertNewConnectionLocation(ind,NewLaunchTime,NewLat,NewLon,NewHeight);
				     
				     if ( (ConnectivityLevel>2) && (!ConnectivityStats) )
				     {
				          // plot conection to new point
				          double xprev = xcurr;
				          double yprev = ycurr;
				          if (ind>0)
				          {
				          	  xprev = inxsize*(ConnectListLon[ind-1]-minlon)/(maxlon-minlon);
				              yprev = inysize*(maxlat - ConnectListLat[ind-1])/(maxlat-minlat);
				          }
				          xcurr = inxsize*(ConnectListLon[ind]-minlon)/(maxlon-minlon);
				          ycurr = inysize*(maxlat - ConnectListLat[ind])/(maxlat-minlat);
						  xnext = inxsize*(ConnectListLon[ind+1]-minlon)/(maxlon-minlon);
						  ynext = inysize*(maxlat - ConnectListLat[ind+1])/(maxlat-minlat);
						  newline1 = new Line(xprev,yprev,xcurr,ycurr);
						  if (CheckVisibility(ind-1, ind))
							 newline1.setStrokeColor(Color.red);	
						  else
							 newline1.setStrokeColor(Color.cyan);	
						  overlay.add(newline1);	
						 
						  // plot connection from new point
						  newline1 = new Line(xcurr,ycurr,xnext,ynext);
						  if (CheckVisibility(ind, ind+1))
							 newline1.setStrokeColor(Color.red);	
						  else
							 newline1.setStrokeColor(Color.cyan);	
						  overlay.add(newline1);
				   	 }
				   }
				}
				else
				{
			       xnext = inxsize*(ConnectListLon[nextind]-minlon)/(maxlon-minlon);
			       ynext = inysize*(maxlat - ConnectListLat[nextind])/(maxlat-minlat);
			       IJ.log("IterConnect<4 and no connectivity between ind="+ind+" and nextind="+nextind+ ", nextlon="+ConnectListLon[nextind]);
			       newline1 = new Line(xcurr,ycurr,xnext,ynext);
				   newline1.setStrokeColor(Color.cyan);
				   if ((ConnectivityLevel>2) && (!ConnectivityStats)) overlay.add(newline1);	
				}
					
			}
    	}
    	
    	if (DebugConnectivity)
    		IJ.log("IterConnectLevel="+IterConnectLevel+", NodesAdded="+NodesAdded+", NodesRemoved="+NodesRemoved);
//		for (int ind=1;ind<ConnectInd;ind++)
//			IJ.log("Lev"+IterConnectLevel+": ind="+ind+", LaunchTime="+ConnectListLaunchTime[ind]+", Lon="+ConnectListLon[ind]+", Lat="+ConnectListLat[ind]);
			
    	IterConnectLevel++;
        if ((NodesAdded>0) || (NodesRemoved>0))
		   if ( (IterConnectLevel<=ConnectivityLevel)) // prevent infinite loop if no convergence
			   RemoveShortLinks(0);   		

    }

    void OrderConnections()
    {
    	// Don't change first and last points (Launch & Landing Ground Stations)
    	int offset = -1;
    	for (int ind=1; ind<ConnectInd-offset-1; ind++)
    	{
//    		IJ.log("ind="+ind+", LaunchTime="+ConnectListLaunchTime[ind]+", Lon="+ConnectListLon[ind]+", Lat="+ConnectListLat[ind]);
    		for (int j=1; j<ConnectInd-ind-offset-1; j++)
    		{
    			if (ConnectListLon[j] < ConnectListLon[j+1])
    			{
    				// swap these two locations
    				float temp = ConnectListLon[j];
    				ConnectListLon[j] = ConnectListLon[j+1];
    				ConnectListLon[j+1] = temp;
    				
    				temp = ConnectListLat[j];
    				ConnectListLat[j] = ConnectListLat[j+1];
    				ConnectListLat[j+1] = temp;
    				
    				temp = ConnectListHt[j];
    				ConnectListHt[j] = ConnectListHt[j+1];
    				ConnectListHt[j+1] = temp;
    				
    				temp = ConnectListLaunchTime[j];
    				ConnectListLaunchTime[j] = ConnectListLaunchTime[j+1];
    				ConnectListLaunchTime[j+1] = temp;
    			}
    		}
    	}
    	
    	if (DebugConnectivity)
    	{
    	   IJ.log("Sorted longitudes:");
    	   for (int j=0; j<ConnectInd; j++) IJ.log("lon["+j+"] = "+ConnectListLon[j]);
    	   IJ.log("LaunchLon[0]="+LaunchLon[0]+", LandingLon[0]="+LandingLon[0]);
    	}
    	
        // Now remove any West of Launch site and East of Landing site (if West-to-East), or vice-versa if East-to-West
        for (int j=0; j<ConnectInd; j++)
    	{
    		if (LaunchLon[0]<LandingLon[0]) // West to East
    		{
    		    if ( (ConnectListLon[j]+DlatlonThresh < LaunchLon[0] ) || (ConnectListLon[j] > LandingLon[0]+DlatlonThresh) )
    			   RemoveBalloons(j,j--); // j-- to ensure we recheck index j after previous one deleted!
    		}
    		else // East to West
     		{
    		    if ( (ConnectListLon[j] > LaunchLon[0]+DlatlonThresh) || (ConnectListLon[j]+DlatlonThresh < LandingLon[0] ) )
    			   RemoveBalloons(j,j--);
    		}
    	}
    	//IJ.log("Removed balloons East or West of Launch/Landing locations");
    	if (DebugConnectivity)
    	{
    	   for (int j=0; j<ConnectInd; j++) 
    	   	   IJ.log("OrderConnections: lon["+j+"] = "+ConnectListLon[j]);
    	}

    	if (ConnectivityLevel==2)
    	{
			double xprev = inxsize*(ConnectListLon[0]-minlon)/(maxlon-minlon);
			double yprev = inysize*(maxlat - ConnectListLat[0])/(maxlat-minlat);
			for (int ind=1;ind<ConnectInd;ind++)
			{
				if (DebugConnectivity) IJ.log("sorted ind="+ind+", LaunchTime="+ConnectListLaunchTime[ind]+", Lon="+ConnectListLon[ind]+", Lat="+ConnectListLat[ind]);
				double xnext = inxsize*(ConnectListLon[ind]-minlon)/(maxlon-minlon);
				double ynext = inysize*(maxlat - ConnectListLat[ind])/(maxlat-minlat);
				Line newline1 = new Line(xprev,yprev,xnext,ynext);

				if (CheckVisibility(ind-1, ind))
				{
				   newline1.setStrokeColor(Color.blue);
//				   IJ.log("lev="+ConnectivityLevel+", blue line between xprev="+xprev+", and xnext= "+xnext);
				}
				else
				{
				   newline1.setStrokeColor(Color.cyan);	
//				   IJ.log("lev=2: cyan line between xprev="+xprev+", and xnext= "+xnext);
				}
				overlay.add(newline1);	
				xprev = xnext;
				yprev = ynext;
			}			
    	}
    	if (DebugConnectivity)
    	{
  		    for (int ind=0;ind<=ConnectInd;ind++)
			   IJ.log("Lev2: ind="+ind+", LaunchTime="+ConnectListLaunchTime[ind]+", Lon="+ConnectListLon[ind]+", Lat="+ConnectListLat[ind]);		
		}
		
    	IterConnectLevel=3;
    	if (ConnectivityLevel>2)
    	   RemoveShortLinks(0);
    }
    
    void CalcConnectivity(int iTime, PrintWriter connectwriter)
    {
    	NumBad = 0;
    	NumGood = 0;
    	double cumulDist = 0;
    	double cumulGeodesicDist = 0;
    	
		double xprev = inxsize*(ConnectListLon[0]-minlon)/(maxlon-minlon);
		double yprev = inysize*(maxlat - ConnectListLat[0])/(maxlat-minlat);
		if (DebugConnectivity)
			if (iTime==240) IJ.log("CalcConnectivity : iTime="+iTime+", ConnectInd="+ConnectInd);
		
		for (int ind=0;ind<=ConnectInd-1;ind++)
		{
			if (CheckVisibility(ind, ind+1))
			{
			   double dist = Math.sqrt(dRangeSq);
			   double distGeodesic = GeodesicDistance(ConnectListLat[ind],ConnectListLon[ind],ConnectListLat[ind+1],ConnectListLon[ind+1]);
			   ConnectListLength[ind] = (float) distGeodesic;
			   cumulDist += dist;
			   cumulGeodesicDist += distGeodesic;
			   if (DebugConnectivity)
			   	   if ((iTime==240)||(iTime==243)) IJ.log("Good: "+ind+" ConnectListLaunchTime[ind]="+ConnectListLaunchTime[ind]+" to "+ConnectListLaunchTime[ind+1]);
			   NumGood++;
			}
			else
			{
				if (DebugConnectivity)
					if ((iTime==240)||(iTime==243)) IJ.log("Bad: "+ind+" ConnectListLaunchTime[ind]="+ConnectListLaunchTime[ind]+" to "+ConnectListLaunchTime[ind+1]);
			    NumBad++;
			}
			if (DebugConnectivity)
			   if ((iTime==240)||(iTime==243)) IJ.log("ind="+ind+", ConnectInd="+ConnectInd+", NumGood="+NumGood+", NumBad="+NumBad);
		}
		if (ConnectInd>0)
		   ConnectListLength[ConnectInd] = 0;
		
		if (NumBad==0)
		{
		   if (DebugConnectivity) IJ.log("iTime="+iTime+",NumBad="+NumBad+", NumGood="+NumGood+", Geodesic Length="+cumulGeodesicDist	);
		}
	    else
	    {
		   if (DebugConnectivity) IJ.log("iTime="+iTime+",NumBad="+NumBad+", NumGood="+NumGood+", No connectivity!");
		   cumulGeodesicDist = 0;
		}
		   	   
	    if (ConnectivityStats)
	    {
	   	    String outputLine = String.format("%4d,%6.1f,%3d",iTime,cumulGeodesicDist,NumGood+NumBad);
	   	    if (cumulGeodesicDist>0) ConnectCount++;
	   	    connectwriter.println(outputLine);
	    }
    }
    
    void GetBalloonsForTimeStep(int iTime)
    {
            int NumLaunchesPerTimeStep = 1; // Doesn't work for anything other than 1!
            NumLaunchesPerTimeStep = NumMetSubSteps;
            xprev = 0;
            yprev = 0;
			xprev = inxsize*(GroundStationLon[1]-minlon)/(maxlon-minlon);
			yprev = inysize*(maxlat - GroundStationLat[1])/(maxlat-minlat);
			
            if (DebugBalloons) 
            	IJ.log("MaxBalloonLaunchTimes="+MaxBalloonLaunchTimes+", MetTimeStep="+MetTimeStep+", NumMetSubSteps="+NumMetSubSteps+", IntegTimeStep="+IntegTimeStep);

            prevConnectInd = ConnectInd;
            
			// Set first connection as landing GS
			ConnectInd = 0;
			ConnectListLat[ConnectInd] = (float) GroundStationLat[1];
			ConnectListLon[ConnectInd] = (float) GroundStationLon[1];
			ConnectListHt[ConnectInd] = 0;
			ConnectListLaunchTime[ConnectInd] = 0;            
            ConnectInd = 1;
            int LatestBalloonInd = 0;
            int LatestBalloonLaunch = 0;
			for (int iLaunch=0;iLaunch<MaxBalloonLaunchTimes;iLaunch++)
			{
				int BalloonInd = (int)(iTime - BalloonLaunchTimes[iLaunch])/(int)IntegTimeStep;
				//if ( (iLaunch>=65) && (iLaunch<=70))
				//   IJ.log("iTime="+iTime+", iLaunch="+iLaunch+", BalloonInd="+BalloonInd+", BalloonLaunchTimes[iLaunch]="+BalloonLaunchTimes[iLaunch]);
				
				if ( (BalloonInd>=0) && (BalloonLaunchTimes[iLaunch]>=0) && (BalloonInd<MaxTrackHours) )
				{
					int ind = iLaunch*MaxTrackHours + BalloonInd;
					//if (iTime==214)  IJ.log("iLaunch="+iLaunch+", BalloonInd="+BalloonInd+", Lon="+BalloonLon[ind]+", Height="+BalloonHt[ind]+", LaunchTime="+BalloonLaunchTimes[iLaunch]);
					if ( (BalloonHt[ind]>0) && (BalloonLaunchTimes[iLaunch]<=iTime) )
					{
						LatestBalloonInd = ind;
						LatestBalloonLaunch = iLaunch;
						double x1 = inxsize*(BalloonLon[ind] - minlon)/(maxlon-minlon);
						double y1 = inysize*(maxlat - BalloonLat[ind])/(maxlat-minlat);
		
						// Need to replace with true line-of-sight length between (lat1,lon1, height1) and (lat2, lon2, height2)
						float linelength = (float) Math.sqrt( (x1-xprev)*(x1-xprev) + (y1-yprev)*(y1-yprev) );
						ConnectListLat[ConnectInd] = BalloonLat[ind];
						ConnectListLon[ConnectInd] = BalloonLon[ind];
						ConnectListHt[ConnectInd] = BalloonHt[ind];
						ConnectListLaunchTime[ConnectInd] = BalloonLaunchTimes[iLaunch];
						ConnectListLength[ConnectInd] = linelength;
						ConnectInd++;
							
						if ((ConnectivityDisplay) && (!ConnectivityStats))
						{
							Line newline1 = new Line(xprev,yprev,x1,y1);
														
							if (ConnectivityLevel==1)
							{
							   if (linelength<120)
							      newline1.setStrokeColor(Color.black);
						       else
						       {
						   	      newline1.setStrokeColor(Color.cyan);
//						   	      IJ.log("Lev=1, ind="+ind+", Lon="+BalloonLon[ind]+", Lat="+BalloonLat[ind]+"xprev="+xprev+", yprev="+yprev);
						   	   }
							   overlay.add(newline1);
							}
						}
						
						// plot Balloon Position
						//if (DebugBalloons) 
						//if (iTime==240) IJ.log("Time="+iTime+", Launch="+iLaunch+", BalloonInd="+BalloonInd+", ind="+ind+", lon="+BalloonLon[ind]+", lat="+BalloonLat[ind]+", x="+x1+", y="+y1);
						if (!ConnectivityStats)
						   if ( (ConnectivityLevel<=2) || (!ConnectivityDisplay) )
						      PlotLocation(BalloonLat[ind], BalloonLon[ind], BalloonHt[ind], MinVisibilityHeight, BalloonLaunchTimes[iLaunch], Color.red);
						xprev = x1; 
						yprev = y1;
						if (NumLaunchesPerTimeStep>1)
						{
							double prevx = x1;
							double prevy = y1;
							int SubInd = ind + 3; // ind+MaxTrackHours; // MM 10Nov19 was... +(int)MetTimeStep; // NB: 3 = time-step used in recording tracks
							double nextx = inxsize*(BalloonLon[SubInd] - minlon)/(maxlon-minlon);
							double nexty = inysize*(maxlat - BalloonLat[SubInd])/(maxlat-minlat);	
							
							for (int subStep=1; subStep<NumLaunchesPerTimeStep; subStep++)
							{
								double frac = (double)subStep/(double)NumLaunchesPerTimeStep;
								double x2 = prevx*(1.0-frac)+nextx*frac;
								double y2 = prevy*(1.0-frac)+nexty*frac;
								if (DebugBalloons) 
									IJ.log("ExtraTime="+iTime+", Launch="+iLaunch+", subStep="+subStep+", frac="+frac+", x="+x2+", y="+y2);

								if (!ConnectivityStats)
								{
									OvalRoi circle = new OvalRoi(x2-size/2,y2-size/2,size,size);
									circle.setStroke(new BasicStroke(2));
									circle.setStrokeColor(Color.red);
									overlay.add(circle);	
								
									if ( (BalloonHt[SubInd]>0) && (VisibilityDisplay) ) // show extent of visibility for min BalloonHeight (eg 15 kms)
									{
										double range = VisibilityRange(BalloonHt[SubInd],MinVisibilityHeight);
										double rangeDeg = RadToDeg*(range/EarthRadius);
										double rangeLat = inysize*rangeDeg/(maxlat-minlat);
										double rangeLon = inxsize*rangeDeg/Math.cos(BalloonLat[SubInd]*DegToRad)/(maxlon-minlon);
										if (DebugBalloons) 
											IJ.log("SubInd="+SubInd+", BallonHeight="+BalloonHt[SubInd]+", range="+range+", rLat="+rangeLat+", rLon="+rangeLon);
										OvalRoi visEllipse = new OvalRoi(x2-rangeLon,y2-rangeLat,rangeLon*2,rangeLat*2);
										visEllipse.setStroke(new BasicStroke(2));
										visEllipse.setStrokeColor(Color.yellow);
										overlay.add(visEllipse);
									}
								}
							}
						}
					}
                }			
			}

			//add launch to end position
			ConnectListLat[ConnectInd] = (float) GroundStationLat[0];
			ConnectListLon[ConnectInd] = (float) GroundStationLon[0];
			ConnectListHt[ConnectInd] = 0;
			ConnectListLaunchTime[ConnectInd] = 0;    
			//IJ.log("ConnectInd="+ConnectInd+", launchLon="+ConnectListLon[ConnectInd]);
	}

			
    void CalcPaths(int iTime, PrintWriter connectwriter, PrintWriter newtrackswriter)
    {
            GetBalloonsForTimeStep(iTime);
			
//			if (!ConnectivityStats)
//			{
				if ( (ConnectivityDisplay) || (ConnectivityStats) )
				{
					if ( (ConnectivityDisplay) && (ConnectivityLevel==1) && (!ConnectivityStats) )
					{
						double xnext = inxsize*(GroundStationLon[0]-minlon)/(maxlon-minlon);
						double ynext = inysize*(maxlat - GroundStationLat[0])/(maxlat-minlat);
						Line newline1 = new Line(xprev,yprev,xnext,ynext);
						// Need to replace with true line-of-sight length between (lat1,lon1, height1) and (lat2, lon2, height2)
						double linelength = Math.sqrt( (xnext-xprev)*(xnext-xprev) + (ynext-yprev)*(ynext-yprev) );
						if (linelength<60)
						   newline1.setStrokeColor(Color.black);
						else
						{
						   newline1.setStrokeColor(Color.cyan);	
	//				       IJ.log("LaunchGS: lev=1: cyan line between xprev="+xprev+", and xnext= "+xnext);
						}
						overlay.add(newline1);
					}
					
					IJ.log("CalcPathsA: iTime="+iTime+", prevNumBad="+NumBad+", prevNumGood="+NumGood);
//					if ((UsePrevLaunchGap==0) || ( (NumBad>0) && (NumGood<=2) ) )
					   OrderConnections();
					   
					IJ.log("CalcPathsB: iTime="+iTime+", UsePrevLaunchGap="+UsePrevLaunchGap+", NumBad="+NumBad+", NumGood="+NumGood+", ConnectInd="+ConnectInd);
					   					   					   
				    //if (iTime==243) // use launch times from previous connected path as basis for current launch time connections
				    if ( (UsePrevLaunchGap!=0) && ( (NumBad==0)  && (NumGood>2) ) )
				    {				    	
				    	// Need to map current ballon positions for each launch time used previously from previous position to current position
				    	int currind = 1;
						//if (iTime==243)	IJ.log("iTime="+iTime+", prevConnectInd="+prevConnectInd+", ConnectInd="+ConnectInd);
						
						for (int prevind=0; prevind<prevConnectInd; prevind++)
				    	{
				    		float LaunchTime = prevConnectListLaunchTime[prevind];
						    //if (iTime==243)	IJ.log("iTime="+iTime+", prevind="+prevind+", prevlaunch[]="+prevConnectListLaunchTime[prevind]);
				    		if (LaunchTime>0)
				    		{
				    			int MatchFound = -1;
								// find in current list (if still present)
								for (int ind=0; ind<ConnectInd; ind++)
								{
									if (ConnectListLaunchTime[ind] == LaunchTime)
										MatchFound = ind;									
								}
								
								if (MatchFound>=0)
								{
									ConnectListLaunchTime[currind] = LaunchTime;
									ConnectListLat[currind] = ConnectListLat[MatchFound];
									ConnectListLon[currind] = ConnectListLon[MatchFound];
									ConnectListHt[currind] = ConnectListHt[MatchFound];
									if ((iTime==240) || (iTime==243))	IJ.log("Copied: currind="+currind+", iTime="+iTime+", prevind="+prevind+", launch[]="+prevConnectListLaunchTime[prevind]+", newLon="+ConnectListLon[currind]+", oldLon="+prevConnectListLon[prevind]);
									currind++;
								}
								else  // need to get lat/lon for current snapshot time from original multitrack trajectories file
								{
									int MatchLaunch = -1;
									for (int iLaunch=0;iLaunch<MaxBalloonLaunchTimes;iLaunch++)
									{
										if (BalloonLaunchTimes[iLaunch] == LaunchTime)
											MatchLaunch = iLaunch;
									}
									
									if (MatchLaunch>=0)
									{
										int BalloonInd = (int)(iTime - BalloonLaunchTimes[MatchLaunch])/(int)IntegTimeStep;									
										int ind = MatchLaunch*MaxTrackHours + BalloonInd;
										if (BalloonHt[ind]>0)
										{
											ConnectListLaunchTime[currind] = LaunchTime;
											ConnectListLat[currind] = BalloonLat[ind];
											ConnectListLon[currind] = BalloonLon[ind];
											ConnectListHt[currind] = BalloonHt[ind];
											if ((iTime==240) || (iTime==243))IJ.log("Retrieved from MultiTracks: MatchLaunch="+MatchLaunch+", BalloonInd="+BalloonInd+", currind="+currind+", iTime="+iTime+", launch[]="+ConnectListLaunchTime[currind]+", newLon="+ConnectListLon[currind]);
											currind++;
										}
										else if ((iTime==240) || (iTime==243))
											IJ.log("Retrieved from MultiTracks: Outside time limits: MatchLaunch="+MatchLaunch+", BalloonInd="+BalloonInd+", launch[]="+ConnectListLaunchTime[currind]);
									}
									else
									{
										IJ.log("*** No match for Launch time = "+LaunchTime+" found in MultiTracks file, so calculate new trajectory...");
										// Need to calculate a new trajectory and (if one ends up within landing zone) add to BalloonList and get lat/lon for current snapshot
										// NB: double LaunchHours = imTimeStep*MetTimeStep/NumMetSubSteps;
										int imTimeStep = (int) (LaunchTime*NumMetSubSteps/MetTimeStep);
										OutputAnyTrackDetails = false;
										OutputBestTrackDetails = true;
										PlotNoHopers = false;
										PlotOnlyGoodTracks = true;
										AlongTrackWinds = false;
										CalcBestTracksForAllSplitIntervals(newtrackswriter,null,imTimeStep,SplittingInterval,imTimeStep,imTimeStep+1,progress);
										NumNewTracks++;
										NumGoodNewTracks += countYellowTracks;
										IJ.log("CalcBestTracksForAllSplitIntervals called; countYellowTracks="+countYellowTracks);
										// Need to extract lat,lon,height values for each timestep... then add to ConnectList[]
										IJ.log("UNDER ACTIVE DEVELOPMENT");
										//System.exit(0);
									}
								}
				    		}
				    	}
				    	
				    	if (currind>0)
				    	{
				    		ConnectInd = currind-1; 
				    		
							//add launch to end position
							ConnectListLat[ConnectInd+1] = (float) GroundStationLat[0];
							ConnectListLon[ConnectInd+1] = (float) GroundStationLon[0];
							ConnectListHt[ConnectInd+1] = 0;
							ConnectListLaunchTime[ConnectInd+1] = 0;    
							
							if ( (iTime==240) || (iTime==243))
							{
								IJ.log("iTime="+iTime+", NumBad="+NumBad+", NumGood="+NumGood+", ConnectInd="+ConnectInd);
								for (int ii=0;ii<=ConnectInd;ii++)
									IJ.log(ii+", Launch="+ConnectListLaunchTime[ii]+", Lat="+ConnectListLat[ii]+", Lon="+ConnectListLon[ii]);
							}
							ConnectInd++;	
						}
						else
							IJ.log("*** Bad balloon position copy - iTime="+iTime+", ConnectInd="+ConnectInd);
				    }					
					// Calculate overall connectivity
					CalcConnectivity(iTime,connectwriter);
				}
//			}   	
    }
    
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)    
    {
        Vector numericFields = gd.getNumericFields();
        int interval;
        
        if (DialogXpos>=0)
           gd.setLocation(DialogXpos,DialogYpos);
 		iheight = (int)gd.getNextNumber();
 		if (iheight>MetNumH-1)
 			iheight = MetNumH-1;
 		
		if (iheight!=previheight)
		{
			IntervalHeight[1] = (double) iheight;
			previheight = iheight;
		}
 		IntervalHeight[0] = (double) iheight;
		
		MetSubStep = (int)gd.getNextNumber();
		
		NumMetSubSteps = (int)gd.getNextNumber();
		if (NumMetSubSteps<1) NumMetSubSteps=1;
		
		TimeStep = (double)gd.getNextNumber();
		MaxTimeSteps = (int) (MaxTimeHours/TimeStep);
		if (NumMetSubSteps>1)
		{
			double HourStep = MetTimeStep/NumMetSubSteps;
			MetTime = String.format("%3.1fhr",HourStep);
			IJ.log("MetTimeStep="+MetTimeStep+", NumMetSubSteps="+NumMetSubSteps+", HourStep="+HourStep+", MetSubStep="+MetSubStep);
		}		
		
		imTimeStep = (int) MetSubStep/NumMetSubSteps;
		DeltaMetSubStep = (double)MetSubStep/NumMetSubSteps - (double)imTimeStep;
		
		if (TestGrib2)
	    {
	    	int NumPerDay = 24/(int)MetTimeStep;
	    	int iDoY = (int) imTimeStep/NumPerDay + iStartDoY; // Need to add offset for startLaunch!
		    imonth = MonthFromDoY(iDoY);
		    iday = iDoY - CumulDoY[imonth];
		    ihour =  (int)MetTimeStep*(imTimeStep - NumPerDay*((int)imTimeStep/NumPerDay));
	    }			
		else if (MetNumT<=12) // Monthly
		   imonth = imTimeStep;
	    else if (MetNumT<=366) // Daily
	    {
		   imonth = MonthFromDoY(imTimeStep);
		   iday = imTimeStep - CumulDoY[imonth];
		}
	    else if (MetNumT<=366*4)// 6-hourly
	    {
	    	int iDoY = (int) imTimeStep/4;
		    imonth = MonthFromDoY(iDoY);
		    iday = iDoY - CumulDoY[imonth];
		    iQuarterDay =  imTimeStep - 4*iDoY;
	    }
	    			
		HeightFraction = (double)gd.getNextNumber();		
		SplittingInterval = (double)gd.getNextNumber();
		MaxSplitIntervals = (int)gd.getNextNumber();
		MaxSplitHalfRange = (int)gd.getNextNumber();
		EndHeight = (int)gd.getNextNumber();
		
		int iheight1 = (int)gd.getNextNumber();
        if (previheight1 != iheight1)
		   IntervalHeight[1] = iheight1; 
	   
		if (SplittingInterval<=0) SplittingInterval=1;
		double dMaxSplitIntervals = MaxTimeHours/SplittingInterval;
		
		for (interval=2;interval<(int)dMaxSplitIntervals;interval++)
			IntervalHeight[interval] = IntervalHeight[1];
		
		previheight1 = iheight1;
		
		//IJ.log("DialogChanged: MaxSplitIntervals="+(int)dMaxSplitIntervals+", h0="+(int)IntervalHeight[0] +", h1="+IntervalHeight[1]+", h2="+(int)IntervalHeight[2] +", h3="+IntervalHeight[3]);
		ShowGeodesic = (boolean)gd.getNextBoolean();
		ShowTracks = (boolean)gd.getNextBoolean();
		ShowGrid = (boolean)gd.getNextBoolean();

		PlotOnlyGoodTracks = (boolean)gd.getNextBoolean();
		PlotNoHopers = (boolean)gd.getNextBoolean();
		if (PlotOnlyGoodTracks)
			PlotNoHopers = false;
		AlongTrackWinds = (boolean)gd.getNextBoolean();
		//YearlyBestTracks = (boolean)gd.getNextBoolean();
		LaunchPeriodDuration = (int)gd.getNextNumber(); // in days
		if (LaunchPeriodDuration>365) LaunchPeriodDuration=365; // limit to one year!
		LaunchPeriodStart = MetSubStep*NumMetSubSteps;
		double LaunchDuration = LaunchPeriodDuration*24*NumMetSubSteps/MetTimeStep;
		LaunchPeriodEnd = LaunchPeriodStart + (int) LaunchDuration;

		//MultiLevelBestTracks = (boolean)gd.getNextBoolean();
		OutputAnyTrackDetails = (boolean)gd.getNextBoolean();
		OutputBestTrackDetails = (boolean)gd.getNextBoolean();
		UseTimeVarying = (boolean)gd.getNextBoolean();

		DlatlonThresh = (double)gd.getNextNumber();
		MetIntervalMultiplier = (int)gd.getNextNumber();
		ShowSites = (boolean)gd.getNextBoolean();
		int prevSelectedLaunchSite = SelectedLaunchSite;
		SelectedLaunchSite = (int)gd.getNextChoiceIndex();
		if (SelectedLaunchSite != prevSelectedLaunchSite)
		{
			LaunchSitesFname = LaunchFiles[SelectedLaunchSite];
			ReadLaunchSitesFile(LaunchSitesFname);
		}
		int prevSelectedLandingSite = SelectedLandingSite;
		SelectedLandingSite = (int)gd.getNextChoiceIndex();
		if (SelectedLandingSite != prevSelectedLandingSite)
		{
			LandingSitesFname = LandingFiles[SelectedLandingSite];
			ReadLandingSitesFile(LandingSitesFname);
		}
		
		// MultiTrack processing..
		int prevSelectedMultiTrackFile = SelectedMultiTrackFile;
		SelectedMultiTrackFile = (int)gd.getNextChoiceIndex();
		if (SelectedMultiTrackFile != prevSelectedMultiTrackFile)
			LoadedBestTracks = false; // force reload of tracks
		//IJ.log("SelectedTrackFile is "+SelectedMultiTrackFile+", "+MultiTrackChoices[SelectedMultiTrackFile]+", "+MultiTrackFiles[SelectedMultiTrackFile]);
		ShowMultiTracks = (boolean)gd.getNextBoolean();
		BalloonLaunchTime = (int) gd.getNextNumber();
		
		boolean prevPlotBalloonsPos = PlotBalloonsPos;
		PlotBalloonsPos = (boolean)gd.getNextBoolean();
		VisibilityDisplay = (boolean)gd.getNextBoolean();
		ConnectivityDisplay = (boolean)gd.getNextBoolean();
		ConnectivityLevel = (int)gd.getNextNumber(); 
		//ConnectivityStats = (boolean)gd.getNextBoolean();				
		UsePrevLaunchGap = (int)gd.getNextNumber(); 
		DialogMaxBalloonTimeSteps = (int)gd.getNextNumber(); 
		
		if (ConfigOpt.compareTo(PrevConfigOpt)!=0)
		{
            IJ.log("Re-read Config file & winds, MetNumImages="+MetNumImages);
            PrevConfigOpt = ConfigOpt;
		}
		
		if (FirstTime==1)
		{
		   ImagePlus imp = IJ.getImage();
		   inxsize = imp.getWidth();     
	       inysize = imp.getHeight();    
  	       //IJ.log("imp: inxsize="+inxsize+", inysize="+inysize);

  	       ReadLaunchSitesFile(LaunchSitesFname);
 	       ReadLandingSitesFile(LandingSitesFname);
  	       ReadGroundStationsFile(GroundStationsFname);
  	       
           ReadLLfile(GeodesicFname);
     
           FirstTime = 0;
       } 
	    
		overlay = new Overlay();
	
		if (ShowGrid)
		   PlotGridField(iheight,imTimeStep,Color.blue);
		
		if (ShowGeodesic)
		   DrawLLseq(NumGeodesic,Color.red,3);

	    if (ShowSites)
	   	   PlotSites();
	   
		if (ShowMultiTracks)
		{
			ReadLaunchSitesFile(LaunchSitesFname);
//			ReadLandingSitesFile(LandingSitesFname);
		    DrawMultiTracks(MultiTrackFname);
		}
	   
	    boolean savShowTracks = ShowTracks;
		if (ShowTracks)
		{
			int site;
			
			for (site=0;site<NumLaunchSites;site++)
			{
				EndLat[site] = 0.0;
				EndTime[site] = 0.0;
				if ( (!MultiLevelBestTracks) && (!YearlyBestTracks) )
			       PlotTracks(writer,Color.orange,true,site,imTimeStep,LaunchLat[site],LaunchLon[site],TimeStep,InterpMode,iheight, HeightFraction, 0);
			   else
			       PlotTracks(writer,Color.orange,false,site,imTimeStep,LaunchLat[site],LaunchLon[site],TimeStep,InterpMode,iheight, HeightFraction, 0);
			}

            double fheight = iheight + HeightFraction;
		}

		if (ConnectivityStats)
		{	        
			CalcConnectivityStats();
		}
		
		if (PlotBalloonsPos)
		{
			// Need to load best tracks if not alreay done so...
			if ( (!LoadedBestTracks) || (!prevPlotBalloonsPos) )
			{
				ReadLaunchSitesFile(LaunchSitesFname);
//				ReadLandingSitesFile(LandingSitesFname);
				LoadBestTracks(MultiTrackFname);
			    ReadGroundStationsFile(GroundStationsFname);
				LoadedBestTracks = true;
			}
			
			if (VisibilityDisplay)  // show extent of visibility fo rmin BalloonHeight (eg 15 kms)
			{
				double range = VisibilityRange(0,MinVisibilityHeight);
				double rangeDeg = RadToDeg*(range/EarthRadius);  
				double rangeLat = inysize*rangeDeg/(maxlat-minlat);
				//IJ.log("Ground Station Visibility: range="+range+", rangeDeg="+rangeDeg+", rangeLat="+rangeLat);
					
				for (int GroundStation=0; GroundStation<NumGroundStations; GroundStation++)
				{
					double x1 = inxsize*(GroundStationLon[GroundStation] - minlon)/(maxlon-minlon);
					double y1 = inysize*(maxlat - GroundStationLat[GroundStation])/(maxlat-minlat);
					double size = 6.0;
					
					OvalRoi circle = new OvalRoi(x1-size/2,y1-size/2,size,size);
					circle.setStroke(new BasicStroke(2));
					circle.setStrokeColor(Color.black);
					overlay.add(circle);
					
					double rangeLon = inxsize*rangeDeg/Math.cos(GroundStationLat[GroundStation]*DegToRad)/(maxlon-minlon);
					OvalRoi visEllipse = new OvalRoi(x1-rangeLon,y1-rangeLat,rangeLon*2,rangeLat*2);
					visEllipse.setStroke(new BasicStroke(2));
					visEllipse.setStrokeColor(Color.blue);
					overlay.add(visEllipse);
				}
			}		
			
			// Now use dedicated slider to step through hours since launch
			int iTime = BalloonLaunchTime; // time in hours
			int inpDoY = (int) (iTime)/24;
			int ihour = 23;
			int iDoY = inpDoY;
			if (inpDoY>364) iDoY=364;
		    int imonth = MonthFromDoY(iDoY);
		    int iday = iDoY - CumulDoY[imonth] + 1;
		    ihour = iTime - 24*iDoY;
		    if (inpDoY>364) ihour = 23;
		    
			String Title = "Hours since T0 for balloon positions: "+iTime+" ("+iday+" "+Month[imonth]+" hour="+ihour+")";
        	Font font = new Font("Arial", Font.PLAIN, 20);
            Roi textRoi = new TextRoi(10, 10, Title, font);
            textRoi.setStrokeColor(Color.black);  
            overlay.add(textRoi);
            
            PrintWriter connectwriter = null;
            CalcPaths(iTime,connectwriter, null);           
		}
				
		if (CalcBestTracks) // for current image timestep, determine best height level for closest track to target destination
		{
    	 	ListDirs(TracksDir);

    	 	NewRunName = LaunchName[0] + "_Run" + NewRunNum;
    	 	new File(TracksDir+"/"+NewRunName).mkdir();
    	 	OutputRunConfigInfo(NewRunName);
			try (
				    FileOutputStream outfile = new FileOutputStream(TracksDir+"/"+NewRunName+"/"+LaunchName[0]+"_Run"+NewRunNum+"_BestTracks_"+MetSubStep+"_Met"+MetTime+".csv");
		            PrintWriter writer = new PrintWriter(outfile);
				)
			{
				OutputBestTrackDetails = true;
				writer.println("LaunchHours,TrackNum,StepTime,Latitude,Longitude,Height,GeodesicRMSE");
				CalcBestTracksForAllSites(writer,imTimeStep,0);
				writer.close();
				OutputBestTrackDetails = false;
			}
			catch (IOException ex) {
                ex.printStackTrace();
            }
			ShowTracks = true; 
		}
		
		if (YearlyBestTracks) // loop over all image timesteps to get per-imTimeStep best track, saving to YearlyBestTracks.csv
		{			                                                                                               
 			runYearly();		
		}

		if (MultiLevelBestTracks) // loop over all Splitting intervals and height levels to get best track, saving to MultiLevelBestTracks.csv
		{
			runMultiLevel();			
		}
//
		ImagePlus imp = IJ.getImage();	
		imp.setOverlay(overlay);

        return true;
    }

    void ll_to_xy(double rlat, double rlon)
    {
    	rx = inxsize*(rlon-minlon)/(maxlon-minlon);
    	ry = inysize*(rlat-minlat)/(maxlat-minlat);
    }

  public void run(ImageProcessor ip) {

  	 // Largely dummy now as main dialog box id modeless, so processing is driven by changes to inputs
  	 
     // get dimensions of input image
	 inxsize = ip.getWidth();     
	 inysize = ip.getHeight();    
  	 IJ.log("inxsize="+inxsize+", inysize="+inysize+", FirstTime="+FirstTime);
  }

}
