library(lubridate)
library(ggplot2)
raw <- read.csv("~/git/shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input/cologneDiseaseImport_Projected.csv")

x <- raw

# first date after previous projections. 
date <-dmy(tail(raw$date,1)) +1

# we iterate day for day until the end of 2032. Each day, we check whether to 
# add a import wave from 2022 or 2023 into the current year. 
while(date<ymd("2032-12-31")){

  date <- date+1

  # default value for cases
  cases <- 1

  # if the date is before Jan 25th, we copy 2023 import into the current year 
  if(month(date) < 2 && mday(date)<=24){
    cases <- x[dmy(x$date)==ymd(paste0("2023","-",month(date),"-",mday(date))),]$cases
  }
  # if the date is after July 4th, we copy 2022's import into the current year 
  if((month(date) == 7 && mday(date) >4) || month(date)>7){
    cases <- x[dmy(x$date)==ymd(paste0("2022","-",month(date),"-",mday(date))),]$cases
  }
  
  
  x[nrow(x) + 1,] = c(format(date,"%d.%m.%y"), cases)
}



ggplot(x) + geom_line(aes(dmy(date),as.numeric(cases)))  


# Here we add the hypothetical easter disease import
# This always begins on april 1 of the year x and continues the same number of 
# days as the autumn import (absolute values are also same, copied to array below)
easter_import <-c(3,11,20,27,34,40,45,50,54,57,59,61,62,63,62,61,59,57,54,50,45,40,34,27,20,11,3)

for(year in 2023:2032){
  
  date <- ymd(paste0(year,"-04-01"))
  for(import_for_day in easter_import){
    x[which(dmy(x$date)==date),"cases"] <- import_for_day
    date <- date + 1
  }
  
}
  

ggplot(x) + geom_line(aes(dmy(date),as.numeric(cases)))  


write.csv(x,file='~/git/shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input/cologneDiseaseImport_Projected_2032.csv', row.names=FALSE, quote = FALSE)
                       