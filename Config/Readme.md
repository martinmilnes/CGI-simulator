Config subdirectory contains almost all the configuration files needed for CGI trajectory simulation software
Comprises:
WindsConfig*.txt = one per met. data set, defining met data files to use, and other configuration parameter values
LaunchSites_*.txt = one per potential launch site, identifying name, lat, lon (used to compile picklist in GUI)
LandingSites_*.txt = one per potential landing site (used to compile picklist in GUI)
GroundStations.txt = file containing two lines - first for GS near launch site, 2nd for GS near landing site
Geodesic.csv = lat/lon co-ords for geodesic optionally plotted on background map
*.png = choice of background maps (actual one defined in ImageJ/P2PHAPSdefaults.txt) - can be any image file in lat/lon projection covering 105W to 15E and 30N to 90N
