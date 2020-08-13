
library(fitdistrplus)
library(dplyr)
library(purrr)
library(ggplot2)
library(reticulate)

use_miniconda(required = TRUE)

np <- import("numpy")

f <- "C:/home/Development/matsim-org/matsim-episim/output-berlin-25pct-superSpreader-calibrParam-1.65E-5/infectionEvents.txt"
f <- "C:/home/Development/matsim-org/matsim-episim/output-berlin-25pct-superSpreader-calibrParam-2.7E-5/infectionEvents.txt"
f <- "C:/home/Development/matsim-org/matsim-episim/output-base/infectionEvents.txt"

counts <- function(f) {

  data <- read.csv(f, sep = "\t")

  df <- table(data[[2]])  
  
  v <- as.numeric(df)
  
  # Persons who did not infect other persons
  no_inf <- setdiff(data$infected, data$infector)
  
  res <- sort(c(rep(0, length(no_inf)), v))
}

agg_counts <- function(f) {
  
  data <- np$load(f)
  as.numeric(data)
}

f <- "C:/home/Development/matsim-org/matsim-episim/src/main/python/analysis/data/dispersion/1.0_aggr.csv.npy"
f <- "C:/home/Development/matsim-org/matsim-episim/battery/v13/params/berlin/1.0_0.28_aggr.npy"

#map(sigma, function(s) { s * 2} )

sigma <- c(0.0, 1.0, 2.0)

s0 <- agg_counts("C:/home/Development/matsim-org/matsim-episim/src/main/python/analysis/data/dispersion/0.0_aggr.csv.npy")
s1 <- agg_counts("C:/home/Development/matsim-org/matsim-episim/src/main/python/analysis/data/dispersion/1.0_aggr.csv.npy") 
s2 <- agg_counts("C:/home/Development/matsim-org/matsim-episim/src/main/python/analysis/data/dispersion/2.0_aggr.csv.npy")



#df <- counts(f)

hist(df, prob=TRUE)

fit <- fitdist(df, fix.arg=list(mu=2.5), "nbinom")
fit


total = sum(df)
s80 <- df[0:-length(df) * 0.8]
sprintf("20%% are responsible for %.2f%%", sum(s80) * 100 / total)



# Testing the distribution
df <- sort(rnbinom(15000, size=1, mu=2.5))
plot(hist(df, breaks = 30))

ggplot() + aes(df) + geom_histogram(binwidth=1, colour="black", fill="white", alpha=0.2)     
