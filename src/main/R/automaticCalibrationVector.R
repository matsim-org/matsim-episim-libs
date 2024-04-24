library(tidyverse)

#THIS IS FOR ASTRA/VECTOR VACCINE!

#Explanation to ease the use of this script: Here, we estimate initialAlpha, initialDelta, initialOmicron
# optimalBeta, halfLife_days and boosterDay are taken from automaticCalibratinMRNA.R
# optimalBeta is set in line 90
# halfLife_days is set in line 86
# boosterDay is set in line 26
# refreshFactor <- 15 (line 91)
#Starting values for the optimization method are set in line 153


# Setting up data frames which contain the VE taken from literature -------

#VE Delta (symptomatic)
deltaFromPapers <- data.frame(matrix(ncol=3,nrow=0))
colnames(deltaFromPapers) <- c("Study", "Days", "VE")
#UKHSA data taken from https://assets.publishing.service.gov.uk/government/uploads/system/uploads/attachment_data/file/1060030/vaccine-surveillance-report-week-10.pdf
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA",28, 0.82)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA",63, 0.78)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA",98, 0.70)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA",133, 0.55)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA",168, 0.49)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA",196, 0.42)

#VE Delta (symptomatic) after receiving booster dose
boosterDay <- 221
#UKHSA data taken fromhttps://assets.publishing.service.gov.uk/government/uploads/system/uploads/attachment_data/file/1060030/vaccine-surveillance-report-week-10.pdf
#Assuming biontech vaccination + biontech booster
deltaFromPapers$Days <- as.numeric(deltaFromPapers$Days)
deltaFromPapers <- filter(deltaFromPapers, Days <= boosterDay)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA, booster",boosterDay+7, 0.88)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA, booster",boosterDay+28, 0.95)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA, booster",boosterDay+63, 0.92)
deltaFromPapers[nrow(deltaFromPapers)+1,] <- c("UKHSA, booster",boosterDay+98, 0.90)

deltaFromPapers$Days <- as.numeric(deltaFromPapers$Days)
deltaFromPapers$VE <- as.numeric(deltaFromPapers$VE)
deltaFromPapers <-deltaFromPapers[order(deltaFromPapers$Days),]


#VE Omicron (symptomatic)
omicronFromPapers <- data.frame(matrix(ncol=3,nrow=0))
colnames(omicronFromPapers) <- c("Study", "Days", "VE")
#UKHSA data taken from https://assets.publishing.service.gov.uk/government/uploads/system/uploads/attachment_data/file/1060030/vaccine-surveillance-report-week-10.pdf
omicronFromPapers[1,] <- c("UKHSA", 28,0.50)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA",63, 0.38)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA",98,0.30)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA",133, 0.19)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA",168, 0.08)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA",196, 0.0)

omicronFromPapers$Days <- as.numeric(omicronFromPapers$Days)
omicronFromPapers <- filter(omicronFromPapers, Days <= boosterDay)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA, booster",boosterDay+7, 0.60)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA, booster",boosterDay+28, 0.62)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA, booster",boosterDay+63, 0.56)
omicronFromPapers[nrow(omicronFromPapers)+1,] <- c("UKHSA, booster",boosterDay+98, 0.39)

omicronFromPapers$Days <- as.numeric(omicronFromPapers$Days)
omicronFromPapers$VE <- as.numeric(omicronFromPapers$VE)
omicronFromPapers <-omicronFromPapers[order(omicronFromPapers$Days),]

#VE Alpha (symptomatic)
alphaFromPapers <- data.frame(matrix(ncol=3,nrow=0))
colnames(alphaFromPapers) <- c("Study", "Days", "VE")
# https://assets.publishing.service.gov.uk/government/uploads/system/uploads/attachment_data/file/1045329/Vaccine_surveillance_report_week_1_2022.pdf
alphaFromPapers[nrow(alphaFromPapers)+1,] <- c("UKHSA", 7, 0.75)
alphaFromPapers[nrow(alphaFromPapers)+1,] <- c("UKHSA", 63, 0.82)
alphaFromPapers[nrow(alphaFromPapers)+1,] <- c("UKHSA", 98, 0.78)

alphaFromPapers$Days <- as.numeric(alphaFromPapers$Days)
alphaFromPapers <- filter(alphaFromPapers, Days <= boosterDay)

alphaFromPapers$Days <- as.numeric(alphaFromPapers$Days)
alphaFromPapers$VE <- as.numeric(alphaFromPapers$VE)
alphaFromPapers <-alphaFromPapers[order(alphaFromPapers$Days),]


# Using the mean square error ---------------------------------------------

# Notation and constants are taken from the model
fact <- 0.001
probaWoVacc <- 1-exp(fact)
halfLife_days <- 61.366
decayConstant <- log(2)/halfLife_days
VE <- rep(1, length(deltaFromPapers$VE)+length(omicronFromPapers$VE))
err <- rep(1, length(deltaFromPapers$VE)+length(omicronFromPapers$VE))
beta <- 1.193
refreshFactor <- 15

# Setting up the function to compute the mean square error
# If you are wondering why I am writing this as a function of "input" instead of a function of nAbDelta, nAbOmicron, beta, refreshFactor: I need this structure for the optim later
calcMSE <- function (input) 
{       nAbDelta <- input[1]
nAbOmicron <- input[2]
nAbAlpha <- input[3]

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
optimalParameters <- optim(c(8,1,20), calcMSE) 
initialDelta <- optimalParameters$par[1]
initialOmicron <- optimalParameters$par[2]
initialAlpha <- optimalParameters$par[3]
optimalBeta <- beta

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

#Computing the VE for both delta and omicron using modelVE
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
  geom_point(data = filter(deltaFromPapers, Study=="UKHSA"), aes(x = Days, y = VE, colour = "UKHSA, Delta")) +
  geom_point(data = filter(deltaFromPapers, Study=="UKHSA, booster"), aes(x = Days, y = VE, colour = "UKHSA, Delta Booster")) +
  geom_point(data = filter(omicronFromPapers, Study=="UKHSA"), aes(x = Days, y = VE, colour="UKHSA, Omicron")) +
  geom_point(data = filter(alphaFromPapers, Study=="UKHSA"), aes(x = Days, y = VE, colour="UKHSA, Alpha")) +
  geom_point(data = filter(omicronFromPapers, Study=="UKHSA, booster"), aes(x = Days, y = VE, colour="UKHSA, Omicron booster")) +
  geom_line(data=modelVEDelta, aes(x=Days, y= VE, colour ="model Delta")) +
  geom_line(data=modelVEOmicron, aes(x=Days,y=VE, colour="model Omicron")) +
  geom_line(data=modelVEAlpha, aes(x=Days,y=VE, colour="model Alpha")) +
  scale_colour_manual("", 
                      breaks = c("UKHSA, Delta", "UKHSA, Delta Booster", "UKHSA, Omicron", "UKHSA, Alpha", "UKHSA, Omicron booster", "model Delta", "model Omicron", "model Alpha"),
                      values = c("navy","dodgerblue2","red", "olivedrab2", "orange", "dodgerblue4", "darkred", "olivedrab4"))
  