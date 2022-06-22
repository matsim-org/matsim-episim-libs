library(tidyverse)

#THIS IS FOR BIONTECH/MRNA VACCINE

#Explanation to ease the use of this script: Here, we estimate initialAlpha, initialDelta, initialOmicron, optimalBeta, halfLife_days and boosterDay
#refreshFactor <- 15 (line 90)
#Starting values for the optimization method are set in line 187

# Setting up data frames which contain the VE taken from literature -------

#VE Delta (symptomatic)
deltaFromPapers <- data.frame(matrix(ncol=3,nrow=0))
colnames(deltaFromPapers) <- c("Study", "Days", "VE")
#Nordstroem data is taken from https://deliverypdf.ssrn.com/delivery.php?ID=663088101094067099122099089103113006019074041037048078090069009097016007119007010122107117023039103016043127065016065021068013031015032054022020124018009103005030084066009086031108101001094010030123020006074124010019020029106079118094126095030121005101&EXT=pdf&INDEX=TRUE
deltaFromPapers[1,] <- c("Nordstroem", 30, 0.92)
deltaFromPapers[nrow(deltaFromPapers)+1,] <-c("Nordstroem",60,0.89)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("Nordstroem",120,0.82)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("Nordstroem",180, 0.48)
deltaFromPapers[nrow(deltaFromPapers)+1,] <-c("Nordstroem",210, 0.32)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("Nordstroem",240, 0.23)
#UKHSA data taken from https://assets.publishing.service.gov.uk/government/uploads/system/uploads/attachment_data/file/1054071/vaccine-surveillance-report-week-6.pdf
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA",28, 0.9)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA",63, 0.83)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA",98, 0.79)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA",133, 0.75)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA",168, 0.68)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA",196, 0.62)
#Andrews data taken from https://www.nejm.org/doi/full/10.1056/NEJMoa2115481
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("Andrews",7, 0.92)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("Andrews",63, 0.90)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("Andrews",98, 0.81)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("Andrews",133, 0.73)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("Andrews",168, 0.66)
# #Buchan data taken from https://www.medrxiv.org/content/10.1101/2021.12.30.21268565v2
# This paper differs too much from the others and was therefore taken out of the consideration
# deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("Buchan",58, 0.89)
# deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("Buchan",119, 0.89)
# deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("Buchan",182, 0.88)
# deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("Buchan",238, 0.85)
# deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("Buchan",266, 0.80)


#VE Omicron (symptomatic)
omicronFromPapers <- data.frame(matrix(ncol=3,nrow=0))
colnames(omicronFromPapers) <- c("Study", "Days", "VE")
#UKHSA data taken from https://assets.publishing.service.gov.uk/government/uploads/system/uploads/attachment_data/file/1058464/Vaccine-surveillance-report-week-9.pdf
omicronFromPapers[1,] <- c("UKHSA", 28,0.63)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA",63, 0.50)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA",98,0.30)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA",133, 0.19)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA",168, 0.15)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA",196, 0.10)
#Chemaitelly data taken from https://www.medrxiv.org/content/10.1101/2022.02.07.22270568v1.full.pdf
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("Chemaitelly",28, 0.62)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("Chemaitelly",56, 0.46)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("Chemaitelly",84, 0.363)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("Chemaitelly",112, 0.29)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("Chemaitelly",140, 0.20)
#Andrews data taken from https://www.medrxiv.org/content/10.1101/2021.12.14.21267615v1.full.pdf Maybe take this one out?
# omicronFromPapers[12,] <- c("Andrews",63, 0.88) Taken this out bc it seems unrealistic
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("Andrews",98, 0.49)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("Andrews",133, 0.34)


#VE Alpha (symptomatic)
alphaFromPapers <- data.frame(matrix(ncol=3,nrow=0))
colnames(alphaFromPapers) <- c("Study", "Days", "VE")
# https://assets.publishing.service.gov.uk/government/uploads/system/uploads/attachment_data/file/1045329/Vaccine_surveillance_report_week_1_2022.pdf
alphaFromPapers[nrow(alphaFromPapers)+1,] <- c("UKHSA", 7, 0.9)
alphaFromPapers[nrow(alphaFromPapers)+1,] <- c("UKHSA", 63, 0.98)
alphaFromPapers[nrow(alphaFromPapers)+1,] <- c("UKHSA", 98, 0.95)

alphaFromPapers$Days <- as.numeric(alphaFromPapers$Days)
alphaFromPapers <- filter(alphaFromPapers, Days <= boosterDay)

alphaFromPapers$Days <- as.numeric(alphaFromPapers$Days)
alphaFromPapers$VE <- as.numeric(alphaFromPapers$VE)
alphaFromPapers <-alphaFromPapers[order(alphaFromPapers$Days),]


# Using the mean square error ---------------------------------------------

# Notation and constants are taken from the model
fact <- 0.001
probaWoVacc <- 1-exp(fact)
# halfLife_days <- 60
# decayConstant <- log(2)/halfLife_days
VE <- rep(1, length(deltaFromPapers$VE)+length(omicronFromPapers$VE))
err <- rep(1, length(deltaFromPapers$VE)+length(omicronFromPapers$VE))

refreshFactor <- 15
# Setting up the function to compute the mean square error
# If you are wondering why I am writing this as a function of "input" instead of a function of nAbDelta, nAbOmicron, beta, refreshFactor: I need this structure for the optim later
calcMSE <- function (input) 
{       nAbDelta <- input[1]
nAbOmicron <- input[2]
nAbAlpha <- input[3]
boosterDay <- input[4]
halfLife_days <- input[5]
beta <- input[6]

decayConstant <- log(2)/halfLife_days

#Delta booster data: As boosterDay is now a free parameter, this had to be moved here
#UKHSA data taken from https://assets.publishing.service.gov.uk/government/uploads/system/uploads/attachment_data/file/1054071/vaccine-surveillance-report-week-6.pdf
#Assuming biontech vaccination + biontech booster
deltaFromPapers$Days <- as.numeric(deltaFromPapers$Days)
deltaFromPapers <- filter(deltaFromPapers, Days <= boosterDay)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA, booster",boosterDay+7, 0.91)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA, booster",boosterDay+28, 0.95)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA, booster",boosterDay+63, 0.91)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA, booster",boosterDay+98, 0.90)

deltaFromPapers$Days <- as.numeric(deltaFromPapers$Days)
deltaFromPapers$VE <- as.numeric(deltaFromPapers$VE)
deltaFromPapers <-deltaFromPapers[order(deltaFromPapers$Days),]

deltaFromPapers <<- deltaFromPapers

#Omicron booster data: As boosterDay is now a free parameter, this had to be moved here
omicronFromPapers$Days <- as.numeric(omicronFromPapers$Days)
omicronFromPapers <- filter(omicronFromPapers, Days <= boosterDay)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA, booster",boosterDay+7, 0.63)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA, booster",boosterDay+28, 0.65)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA, booster",boosterDay+63, 0.58)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA, booster",boosterDay+98, 0.42)

omicronFromPapers$Days <- as.numeric(omicronFromPapers$Days)
omicronFromPapers$VE <- as.numeric(omicronFromPapers$VE)
omicronFromPapers <-omicronFromPapers[order(omicronFromPapers$Days),]

omicronFromPapers <<- omicronFromPapers

#In this first for loop we are solely dealing with delta
for(i in 1:length(deltaFromPapers$Days)){ 
  if(deltaFromPapers$Days[i] <= boosterDay){
    nAbOnDay <- nAbDelta * exp(-decayConstant*deltaFromPapers$Days[i])
  }
  if(deltaFromPapers$Days[i] > boosterDay){
    nAbOnDay240 <- nAbDelta * exp(-decayConstant*boosterDay)
    nAbOnDay241 <- nAbOnDay240*refreshFactor
    nAbOnDay <- nAbOnDay241 * exp(-decayConstant*(deltaFromPapers$Days[i]-boosterDay))
  }
  immunityFactor <- 1 / (1 + nAbOnDay^beta)
  probaWVacc <- 1 - exp(-fact*immunityFactor)
  probaWoVacc <- 1-exp(-fact)
  VE[i] <- 1- probaWVacc/probaWoVacc
  err[i] <- deltaFromPapers$VE[i] - VE[i]
}
#We are now dealing with omicron
for(i in 1:length(omicronFromPapers$Days)){
  if(omicronFromPapers$Days[i] <= boosterDay){
    nAbOnDay <- nAbOmicron * exp(-decayConstant*omicronFromPapers$Days[i])
  }
  if(omicronFromPapers$Days[i] > boosterDay){
    nAbOnDay240 <- nAbOmicron * exp(-decayConstant*boosterDay)
    nAbOnDay241 <- nAbOnDay240*refreshFactor
    nAbOnDay <- nAbOnDay241 * exp(-decayConstant*(omicronFromPapers$Days[i]-boosterDay))
  }
  immunityFactor <- 1 / (1 + nAbOnDay^beta)
  probaWVacc <- 1 - exp(-fact*immunityFactor)
  probaWoVacc <- 1-exp(-fact)
  VE[i+length(deltaFromPapers$Days)] <- 1- probaWVacc/probaWoVacc
  err[i+length(deltaFromPapers$Days)] <- omicronFromPapers$VE[i] - VE[i+length(deltaFromPapers$Days)]
}
sum(err^2) / length(err)
#We are now dealing with alpha
for(i in 1:length(alphaFromPapers$Days)){
  if(alphaFromPapers$Days[i] <= boosterDay){
    nAbOnDay <- nAbAlpha * exp(-decayConstant*alphaFromPapers$Days[i])
  }
  if(alphaFromPapers$Days[i] > boosterDay){
    nAbOnDay240 <- nAbAlpha * exp(-decayConstant*boosterDay)
    nAbOnDay241 <- nAbOnDay240*refreshFactor
    nAbOnDay <- nAbOnDay241 * exp(-decayConstant*(alphaFromPapers$Days[i]-boosterDay))
  }
  immunityFactor <- 1 / (1 + nAbOnDay^beta)
  probaWVacc <- 1 - exp(-fact*immunityFactor)
  probaWoVacc <- 1-exp(-fact)
  VE[i+length(deltaFromPapers$Days)+length(omicronFromPapers$Days)] <- 1- probaWVacc/probaWoVacc
  err[i+length(deltaFromPapers$Days)+length(omicronFromPapers$Days)] <- alphaFromPapers$VE[i] - VE[i+length(deltaFromPapers$Days)+length(omicronFromPapers$Days)]
}
sum(err^2) / length(err)
}

#Computing initial nAb for Delta and Omicron as well as optimal beta
optimalParameters <- optim(c(8,1,20,200,70, 1), calcMSE) 
initialDelta <- optimalParameters$par[1]
initialOmicron <- optimalParameters$par[2]
initialAlpha <- optimalParameters$par[3]
boosterDay <- optimalParameters$par[4]
halfLife_days <- optimalParameters$par[5]
optimalBeta <- optimalParameters$par[6]


# From here on forward: Everything serves the purpose of visualization --------

#Setting up a function to evaluate VE using the computed parameters
modelVE <- function(initialNAb, refreshFactor, beta, day){
  if(day <= boosterDay){
    nAbOnDay <- initialNAb * exp(-decayConstant*day)
  }
  if(day > boosterDay){
    nAbOnDay240 <- initialNAb * exp(-decayConstant*boosterDay)
    nAbOnDay241 <- nAbOnDay240*refreshFactor
    nAbOnDay <- nAbOnDay241 * exp(-decayConstant*(day-boosterDay))
  }
  immunityFactor <- 1 / (1 + nAbOnDay^beta)
  probaWVacc <- 1 - exp(-fact*immunityFactor)
  probaWoVacc <- 1-exp(-fact)
  1- probaWVacc/probaWoVacc
}

#Computing the VE for delta, omicron and alpha using modelVE
modelVEDelta <- data.frame(matrix(ncol=2,nrow=300))
colnames(modelVEDelta) <- c("Days", "VE")
for(i in 1:300){
  modelVEDelta[i,]=c(i, modelVE(initialDelta,refreshFactor, optimalBeta,i))
}

modelVEOmicron <- data.frame(matrix(ncol=2,nrow=300))
colnames(modelVEOmicron) <- c("Days", "VE")
for(i in 1:300){
  modelVEOmicron[i,]=c(i, modelVE(initialOmicron,refreshFactor,optimalBeta,i))
}

modelVEAlpha <- data.frame(matrix(ncol=2,nrow=300))
colnames(modelVEAlpha) <- c("Days", "VE")
for(i in 1:300){
  modelVEAlpha[i,]=c(i, modelVE(initialAlpha,refreshFactor,optimalBeta,i))
}

ggplot() +
  geom_point(data = filter(deltaFromPapers, Study=="Nordstroem"), aes(x = Days, y = VE, colour = "Nordstroem, Delta")) +
  geom_point(data = filter(deltaFromPapers, Study=="UKHSA"), aes(x = Days, y = VE, colour = "UKHSA, Delta")) +
  geom_point(data = filter(deltaFromPapers, Study=="Andrews"), aes(x = Days, y = VE, colour = "Andrews, Delta")) +
  geom_point(data = filter(deltaFromPapers, Study=="UKHSA, booster"), aes(x = Days, y = VE, colour = "UKHSA, Delta Booster")) +
  geom_point(data = filter(omicronFromPapers, Study=="UKHSA"), aes(x = Days, y = VE, colour="UKHSA, Omicron")) +
  geom_point(data = filter(omicronFromPapers, Study=="Chemaitelly"), aes(x = Days, y = VE, colour="Chemaitelly, Omicron")) +
  geom_point(data = filter(alphaFromPapers, Study=="UKHSA"), aes(x = Days, y = VE, colour="UKHSA, Alpha")) +
  geom_point(data = filter(omicronFromPapers, Study=="Andrews"), aes(x = Days, y = VE, colour="Andrews, Omicron")) +
  geom_point(data = filter(omicronFromPapers, Study=="UKHSA, booster"), aes(x = Days, y = VE, colour="UKHSA, Omicron booster")) +
  geom_line(data=modelVEDelta, aes(x=Days, y= VE, colour ="model Delta")) +
  geom_line(data=modelVEOmicron, aes(x=Days,y=VE, colour="model Omicron")) +
  geom_line(data=modelVEAlpha, aes(x=Days,y=VE, colour="model Alpha")) +
  scale_colour_manual("", 
                      breaks = c("Nordstroem, Delta", "UKHSA, Delta", "Andrews, Delta", "UKHSA, Delta Booster", "UKHSA, Omicron", "Chemaitelly, Omicron", "Andrews, Omicron", "UKHSA, Alpha", "UKHSA, Omicron booster", "model Delta", "model Omicron", "model Alpha"),
                      values = c("navy","dodgerblue2","dodgerblue3", "lightblue","red", "coral", "tomato4", "olivedrab2", "orange", "dodgerblue4", "darkred", "olivedrab4"))
