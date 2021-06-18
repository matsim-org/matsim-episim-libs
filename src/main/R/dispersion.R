
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

est_disp <- function(f, mu=1) {
  
  matrix <- np$load(f)
  
  est <- list()
  inf80 <- list()
  
  # Loaded vector is one row
  v <- as.numeric(matrix)
  #for(row in 1:nrow(matrix)) {
    
      #sprintf("Processing row %d\n", row)
      #v <- matrix[row,]
      v <- v[!is.na(v)]
      
      if (length(v) == 0) {
          next
      }
      
      fit <- try(fitdist(v, fix.arg=list(mu=mu), "nbinom"), silent = T)
      if(inherits(fit, "try-error")) {
        fit <- list(estimate=NaN)
      }
      
      est <- c(est, as.numeric(fit$estimate))
      
      total = sum(v)
      s80 <- v[0:-length(v) * 0.8]
      
      inf80 <- c(inf80, sum(s80) * 100 / total)
      
  #}

  df <- data.frame(as.numeric(est), as.numeric(inf80))
  colnames(df) <- c("est", "top20")
  
  return(df)
}


est_disp_zip <- function(f) {
  
  tmpdir <- tempdir()
  unzip(f, exdir = tmpdir )
  
  res <- data.frame()
  
  for (f in list.files(tmpdir, pattern = "*.npy", full.names = T)) {
    df <- est_disp(f)
    means <- colMeans(df, na.rm = T)
    df <- data.frame(t(means))
    
    row.names(df) <- c(basename(f)) 
    
    res <- rbind(res, df)
  }
 
  return(res) 
}

setwd("C:/Users/chris/Development/matsim-org/matsim-episim/biggest-jan")

est_disp("0.46.npy")
est_disp("0.56.npy")

# Testing the distribution
df <- sort(rnbinom(15000, size=1, mu=2.5))
plot(hist(df, breaks = 30))

ggplot() + aes(df) + geom_histogram(binwidth=1, colour="black", fill="white", alpha=0.2)     
