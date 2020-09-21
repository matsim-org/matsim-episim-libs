
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


f <- "C:/home/Development/matsim-org/matsim-episim/src/main/python/analysis/data/dispersion/1.0_aggr.npy"


sigma <- c(0.0, 1.0, 2.0)

est_disp <- function(f) {
  
  matrix <- np$load(f)
  
  est <- list()
  inf80 <- list()
  
  for(row in 1:nrow(matrix)) {
    
      sprintf("Processing row %d\n", row)
      v <- matrix[row,]
      v <- v[!is.na(v)]
      
      fit <- fitdist(v, fix.arg=list(mu=2.5), "nbinom")
      est <- c(est, as.numeric(fit$estimate))
      
      total = sum(v)
      s80 <- v[0:-length(v) * 0.8]
      
      inf80 <- c(inf80, sum(s80) * 100 / total)
      
  }

  df <- data.frame(as.numeric(est), as.numeric(inf80))
  colnames(df) <- c("est", "top20")
  
  return(df)
}

d0 <- est_disp("C:/home/Development/matsim-org/matsim-episim/src/main/python/analysis/data/dispersion/DEFAULT_1_aggr.npy")
d1 <- est_disp("C:/home/Development/matsim-org/matsim-episim/src/main/python/analysis/data/dispersion/DEFAULT_3_aggr.npy")
d2 <- est_disp("C:/home/Development/matsim-org/matsim-episim/src/main/python/analysis/data/dispersion/DEFAULT_10_aggr.npy")

s0 <- est_disp("C:/home/Development/matsim-org/matsim-episim/src/main/python/analysis/data/dispersion/SYMMETRIC_1_aggr.npy")
s1 <- est_disp("C:/home/Development/matsim-org/matsim-episim/src/main/python/analysis/data/dispersion/SYMMETRIC_3_aggr.npy")
s2 <- est_disp("C:/home/Development/matsim-org/matsim-episim/src/main/python/analysis/data/dispersion/SYMMETRIC_10_aggr.npy")
s3 <- est_disp("C:/home/Development/matsim-org/matsim-episim/src/main/python/analysis/data/dispersion/SYMMETRIC_30_aggr.npy")

#df <- counts(f)
#hist(df, prob=TRUE)


# Testing the distribution
df <- sort(rnbinom(15000, size=1, mu=2.5))
plot(hist(df, breaks = 30))

ggplot() + aes(df) + geom_histogram(binwidth=1, colour="black", fill="white", alpha=0.2)     
