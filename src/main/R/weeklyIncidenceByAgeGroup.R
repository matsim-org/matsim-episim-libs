library(openxlsx)
library(tidyverse)
library(lubridate)

#Reading Infection data and how LandkreisIds translate to Landskreis names
#Rohdaten <- read.csv("https://github.com/robert-koch-institut/SARS-CoV-2_Infektionen_in_Deutschland/blob/master/Aktuell_Deutschland_SarsCov2_Infektionen.csv?raw=true")
Rohdaten <- read.csv("../../../git/robert-koch-insitut/SARS-CoV-2_Infektionen_in_Deutschland/Aktuell_Deutschland_SarsCov2_Infektionen.csv" )
Landkreisnamen <- read.xlsx("https://www.destatis.de/DE/Themen/Laender-Regionen/Regionales/Gemeindeverzeichnis/Administrativ/04-kreise.xlsx?__blob=publicationFile", sheet=2)

#Preparing output
# TotalNoOfCasesAndWeeklyIncidence <- data.frame(Datum=as.Date(character()), IdLandkreis = character(),
#                                                NameLandkreis = character(), A00A04 = integer(), A05A14 = integer(),
#                                                A15A34 = integer(), A35A59 = integer(), A60A79=integer(), A80plus=integer(),
#                                                Aunbekannt=integer(), weeklyIncidenceA00A04=integer(), weeklyIncidenceA05A14=integer(),
#                                                weeklyIncidenceA15A34=integer(), weeklyIncidenceA35A59=integer(),
#                                                weeklyIncidenceA60A79=integer())
TotalNoOfCasesAndWeeklyIncidence <- data.frame(Datum=as.Date(character()), IdLandkreis = character(),
                                               NameLandkreis = character(), weeklyIncidenceA00A04=integer(), weeklyIncidenceA05A14=integer(),
                                               weeklyIncidenceA15A34=integer(), weeklyIncidenceA35A59=integer(),
                                               weeklyIncidenceA60A79=integer())


#Preparing population numbers

# read table:
Altersgruppen <- read.xlsx("https://www.dropbox.com/scl/fi/wnw2e1ineet0guunkrc37/Altersgruppen.xlsx?dl=1&rlkey=xji6z0yn2oy2dd87xa702iqth")

# for some Landkreise, there are only "-" as entry.  Remove those rows:
Altersgruppen <- subset(Altersgruppen, Altersgruppen$unter.3.Jahre!="-")

# force column entries to be numbers.  I guess they were read as String, possibly because of the "-" entries removed above.
Altersgruppen$unter.3.Jahre = as.numeric(Altersgruppen$unter.3.Jahre)
Altersgruppen$`3.bis.unter.6.Jahre` = as.numeric(Altersgruppen$`3.bis.unter.6.Jahre`)
Altersgruppen$`6.bis.unter.10.Jahre` = as.numeric(Altersgruppen$`6.bis.unter.10.Jahre`)
Altersgruppen$`10.bis.unter.15.Jahre` = as.numeric(Altersgruppen$`10.bis.unter.15.Jahre`)
Altersgruppen$`15.bis.unter.18.Jahre` = as.numeric(Altersgruppen$`15.bis.unter.18.Jahre`)
Altersgruppen$`18.bis.unter.20.Jahre` = as.numeric(Altersgruppen$`18.bis.unter.20.Jahre`)
Altersgruppen$`20.bis.unter.25.Jahre` = as.numeric(Altersgruppen$`20.bis.unter.25.Jahre`)
Altersgruppen$`25.bis.unter.30.Jahre` = as.numeric(Altersgruppen$`25.bis.unter.30.Jahre`)
Altersgruppen$`30.bis.unter.35.Jahre` = as.numeric(Altersgruppen$`30.bis.unter.35.Jahre`)
Altersgruppen$`35.bis.unter.40.Jahre` = as.numeric(Altersgruppen$`35.bis.unter.40.Jahre`)
Altersgruppen$`40.bis.unter.45.Jahre` = as.numeric(Altersgruppen$`40.bis.unter.45.Jahre`)
Altersgruppen$`45.bis.unter.50.Jahre` = as.numeric(Altersgruppen$`45.bis.unter.50.Jahre`)
Altersgruppen$`50.bis.unter.55.Jahre` = as.numeric(Altersgruppen$`50.bis.unter.55.Jahre`)
Altersgruppen$`55.bis.unter.60.Jahre` = as.numeric(Altersgruppen$`55.bis.unter.60.Jahre`)
Altersgruppen$`60.bis.unter.65.Jahre` = as.numeric(Altersgruppen$`60.bis.unter.65.Jahre`)
Altersgruppen$`65.bis.unter.75.Jahre` = as.numeric(Altersgruppen$`65.bis.unter.75.Jahre`)
Altersgruppen$`75.Jahre.und.mehr` = as.numeric(Altersgruppen$`75.Jahre.und.mehr`)
Altersgruppen$Insgesamt = as.numeric(Altersgruppen$Insgesamt)

#Creating the age groups st they match the age groups from the RKI data
#Attention! We assume: uniform distribution of age 3, 4, 5 in the age group 3-5
#Also! Population data has the age group 60-75, while RKI has 60-79
Altersgruppen <- mutate(Altersgruppen, A00A04 = Altersgruppen$unter.3.Jahre+2/3*Altersgruppen$`3.bis.unter.6.Jahre`) 
Altersgruppen <- mutate(Altersgruppen, A05A14 = 1/3*Altersgruppen$`3.bis.unter.6.Jahre`+Altersgruppen$`6.bis.unter.10.Jahre`+Altersgruppen$`10.bis.unter.15.Jahre`) 
Altersgruppen <- mutate(Altersgruppen, A15A34 = Altersgruppen$`15.bis.unter.18.Jahre`+Altersgruppen$`18.bis.unter.20.Jahre`+Altersgruppen$`20.bis.unter.25.Jahre`+Altersgruppen$`20.bis.unter.25.Jahre`+Altersgruppen$`25.bis.unter.30.Jahre`+Altersgruppen$`30.bis.unter.35.Jahre`) 
Altersgruppen <- mutate(Altersgruppen, A35A59 = Altersgruppen$`35.bis.unter.40.Jahre`+Altersgruppen$`40.bis.unter.45.Jahre`+Altersgruppen$`45.bis.unter.50.Jahre`+Altersgruppen$`50.bis.unter.55.Jahre`+Altersgruppen$`55.bis.unter.60.Jahre`) 
Altersgruppen <- mutate(Altersgruppen, A60A79 = Altersgruppen$`60.bis.unter.65.Jahre`+Altersgruppen$`65.bis.unter.75.Jahre`) 

#Computing no of cases as well as incidence for all regions
ListOfLandkreisIds <- unique(Rohdaten$IdLandkreis)
ListOfAgeGroups <- unique(Rohdaten$Altersgruppe)


# filter the raw data (Rohdaten) by the district ID we are interested in, so that the result (subsetData) becomes a bit smaller:

# for(Id in ListOfLandkreisIds){
Id <- 5315 # Köln
# Id <- 11001 # Berlin
  if (Id == 11001 || Id == 11002 || Id == 11003 || Id == 11004 || Id == 11005 || Id == 11006 || Id == 11007 || Id == 11008 || Id == 11009 || Id == 11010 || Id == 11011 || Id == 11012){
    subsetData <- filter(Rohdaten, between(IdLandkreis, 11001, 11012))
    stadtname <- "Berlin"
    Id <- 11000
  } else {
    subsetData <- filter(Rohdaten, IdLandkreis == Id)
  }

# convert Meldedatum column to a true date column:
  subsetData$Meldedatum <- as.Date(subsetData$Meldedatum)

# extract all existing dates:
  listMeldedaten <- sort(unique(subsetData$Meldedatum))

# The data comes in the form of the RKI records.  Each combination of (IdLandkreis, Geschlecht, Altersgruppe, Meldedatum, Refdatum, IstErkrankungsbeginn) forms a group.
# For each group, they report
# --> number of infected, number of deceased, number of recovered <--
# For some unexplained reason, exactly one of these numbers is different from zero, i.e. if necessary they end up splitting the data into
#    group nInfected 0 0
#    group 0 nDeceaseed
#    group 0 0 nRecovered
# If all three numbers are zero, the group is (presumably) not reported.

# Records can be corrected later.  This then results in columns NeuerFall, NeuerTodesfall, NeuGenesen, where supposedly again exactly one of them will
# be different from zero.  The for us most important keys are 1 and -1 for NeuerFall.  These only make a statement if the record already existed in
# the version of the file from the previous day.  We essentially do not care about that, so "1" (= new) and "0" (= pre-existing) have the same
# meaning.  Unfortunately, "-1" needs to be considered, since it means that the record is removed (i.e. it will not be there on the next day).
# (One also takes from this that the "1" and "-1" only occur for records of the last couple of days ... which is indeed the case.)  --  I think,
# however, that the following code simply ignores this "NeuerFall" issue.  kai, nov'21

  
  # weeklySumA00A04 = {}
  # weeklySumA05A14 = {}
  # weeklySumA15A34 = {}
  # weeklySumA35A59 = {}
  # weeklySumA60A79 = {}
  # weeklySumA80plus = {}
  # weeklySumAunbekannt = {}
  weeklyIncidenceA00A04 = {}
  weeklyIncidenceA05A14 = {}
  weeklyIncidenceA15A34 = {}
  weeklyIncidenceA35A59 = {}
  weeklyIncidenceA60A79 = {}
  datum ={}
  stadtname ={}
  rownumber={}

date = min(listMeldedaten)+days(0)
# (what we would want is to start on the last date and go back by 7day steps (= weekly averaged).  kai, nov'21)

while(date <= max(listMeldedaten)){
  datum <- append(datum, date)
{
  # filter the subsetData for age group and 7-day period:
    A00A04 <- filter(subsetData, Altersgruppe=="A00-A04")
    A00A04 <- filter(A00A04, between(Meldedatum, date-days(7), date))

  # sum the number of cases for the filtered data:
    SumAnzahl <- sum(A00A04$AnzahlFall)
  # assert_that( is.numeric(SumAnzahl))

  # find index of cell which contains population size:
    idAltersgruppen <- which(Altersgruppen$landkreisId == Id)

  # compute and append weekly incidence:
    weeklyIncidenceA00A04 <- append(weeklyIncidenceA00A04,SumAnzahl/Altersgruppen$A00A04[idAltersgruppen]*100000)

    # weeklySumA00A04 <- append(weeklySumA00A04, SumAnzahl)
}
{
    A05A14 <- filter(subsetData, Altersgruppe=="A05-A14") 
    A05A14 <- filter(A05A14, between(Meldedatum, date-days(7), date))
    SumAnzahl <- sum(A05A14$AnzahlFall)
    # idAltersgruppen <- which(Altersgruppen$landkreisId == Id)
    weeklyIncidenceA05A14 <- append(weeklyIncidenceA05A14,SumAnzahl/Altersgruppen$A05A14[idAltersgruppen]*100000)
    # weeklySumA05A14 <- append(weeklySumA05A14, SumAnzahl)
}
    
    A15A34 <- filter(subsetData, Altersgruppe=="A15-A34") 
    A15A34 <- filter(A15A34, between(Meldedatum, date-days(7), date))
    SumAnzahl <- sum(A15A34$AnzahlFall)
    # idAltersgruppen <- which(Altersgruppen$landkreisId == Id)
    weeklyIncidenceA15A34 <- append(weeklyIncidenceA15A34,SumAnzahl/Altersgruppen$A15A34[idAltersgruppen]*100000)
    # weeklySumA15A34 <- append(weeklySumA15A34, SumAnzahl)
    
    A35A59 <- filter(subsetData, Altersgruppe=="A35-A59") 
    A35A59 <- filter(A35A59, between(Meldedatum, date-days(7), date))
    SumAnzahl <- sum(A35A59$AnzahlFall)
    # idAltersgruppen <- which(Altersgruppen$landkreisId == Id)
    weeklyIncidenceA35A59 <- append(weeklyIncidenceA35A59,SumAnzahl/Altersgruppen$A35A59[idAltersgruppen]*100000)
    # weeklySumA35A59 <- append(weeklySumA35A59, SumAnzahl)
    
    A60A79 <- filter(subsetData, Altersgruppe=="A60-A79") 
    A60A79 <- filter(A60A79, between(Meldedatum, date-days(7), date))
    SumAnzahl <- sum(A60A79$AnzahlFall)
    # idAltersgruppen <- which(Altersgruppen$landkreisId == Id)
    weeklyIncidenceA60A79 <- append(weeklyIncidenceA60A79,SumAnzahl/Altersgruppen$A60A79[idAltersgruppen]*100000)
    # weeklySumA60A79 <- append(weeklySumA60A79, SumAnzahl)
    
    # A80plus <- filter(subsetData, Altersgruppe=="A80+")
    # A80plus <- filter(A80plus, between(Meldedatum, date-days(7), date))
    # SumAnzahl <- sum(A80plus$AnzahlFall)
    # weeklySumA80plus <- append(weeklySumA80plus, SumAnzahl)
    
    # Aunbekannt <- filter(subsetData, Altersgruppe=="unbekannt")
    # Aunbekannt <- filter(Aunbekannt, between(Meldedatum, date-days(7), date))
    # SumAnzahl <- sum(Aunbekannt$AnzahlFall)
    # weeklySumAunbekannt <- append(weeklySumAunbekannt, SumAnzahl)

    date <- date+days(7)


}

# print lengths of vectors if they have equal length:
print( paste( length(weeklyIncidenceA00A04), length(weeklyIncidenceA05A14), length(weeklyIncidenceA15A34), length(weeklyIncidenceA35A59), length(weeklyIncidenceA60A79) ) )

    for(Idlist in Landkreisnamen$"Kreisfreie.Städte.und.Landkreise.nach.Fläche,.Bevölkerung.und.Bevölkerungsdichte"){
      if(grepl(Id, Idlist)){
        rownumber <- which(Landkreisnamen$"Kreisfreie.Städte.und.Landkreise.nach.Fläche,.Bevölkerung.und.Bevölkerungsdichte" == Idlist)
        stadtname <- Landkreisnamen$"X3"[rownumber]
      }
    }
    landkreisId <- rep(Id, length(datum))
    nameLandkreis <- rep(stadtname, length(datum))

# dataframe <- data.frame(datum, landkreisId, nameLandkreis, weeklySumA00A04, weeklySumA05A14, weeklySumA15A34, weeklySumA35A59,
#                         weeklySumA60A79, weeklySumA80plus, weeklySumAunbekannt, weeklyIncidenceA00A04, weeklyIncidenceA05A14,
#                         weeklyIncidenceA15A34, weeklyIncidenceA35A59, weeklyIncidenceA60A79)
dataframe <- data.frame(datum, landkreisId, nameLandkreis, weeklyIncidenceA00A04, weeklyIncidenceA05A14,
                        weeklyIncidenceA15A34, weeklyIncidenceA35A59, weeklyIncidenceA60A79)
TotalNoOfCasesAndWeeklyIncidence <- rbind(TotalNoOfCasesAndWeeklyIncidence, dataframe)
# }

ggplot() + scale_y_log10(limits = c(30,2000)) +
  geom_point( data=dataframe, mapping=aes(x=datum,y=weeklyIncidenceA00A04),color="purple") +
  geom_point( data=dataframe, mapping=aes(x=datum,y=weeklyIncidenceA05A14),color="red") +
  geom_line( data=dataframe, mapping=aes(x=datum,y=weeklyIncidenceA15A34),color="orange") +
  geom_line( data=dataframe, mapping=aes(x=datum,y=weeklyIncidenceA35A59),color="green") +
  geom_line( data=dataframe, mapping=aes(x=datum,y=weeklyIncidenceA60A79),color="blue") +
  scale_x_date( date_breaks = "2 weeks", limits = as.Date(c('2021-08-01','2021-11-30')) )

