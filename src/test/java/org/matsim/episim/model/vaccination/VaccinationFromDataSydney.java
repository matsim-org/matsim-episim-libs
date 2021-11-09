package org.matsim.episim.model.vaccination;

import tech.tablesaw.api.*;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.selection.Selection;
import java.net.URL;
import java.time.LocalDate;
import static tech.tablesaw.api.ColumnType.*;
import static tech.tablesaw.api.ColumnType.INTEGER;

public class VaccinationFromDataSydney {

	public static void main(String[] args) throws Exception {
		ColumnType[] types = {LOCAL_DATE, STRING, STRING, INTEGER, INTEGER};
		String location =
				"https://raw.githubusercontent.com/robert-koch-institut/COVID-19-Impfungen_in_Deutschland/master/Aktuell_Deutschland_Landkreise_COVID-19-Impfungen.csv";
		Table rkiData = Table.read().usingOptions(CsvReadOptions.builder(new URL(location))
				.tableName("rkidata")
				.columnTypes(types));

		IntColumn impfschutz = rkiData.intColumn("Impfschutz");
		Selection erstimpfung = impfschutz.isEqualTo(1);
		Table cologne = rkiData.where(erstimpfung);
		StringColumn cologne2 = cologne.stringColumn("LandkreisId_Impfort");
		Selection koeln = cologne2.isEqualTo("05315");
		Table cologneTable = cologne.where(koeln);
		StringColumn alter = cologneTable.stringColumn("Altersgruppe");
		Selection zwoelf17 = alter.isEqualTo("12-17");
		Selection achtzehn59 = alter.isEqualTo("18-59");
		Selection sechzigplus = alter.isEqualTo("60+");

		// Dealing with 12-17 year olds
		Table youngpeople = cologneTable.where(zwoelf17);
		youngpeople.sortAscendingOn("Impfdatum");
		DoubleColumn cumsumAnzahl = youngpeople.intColumn("Anzahl").cumSum();
		DoubleColumn quoteyoungColumn = cumsumAnzahl.divide(54587.2);
		quoteyoungColumn.setName("12-17");
		youngpeople.addColumns(quoteyoungColumn);
		youngpeople.removeColumns("Impfschutz", "LandkreisId_Impfort", "Altersgruppe", "Anzahl");
		youngpeople.column("Impfdatum").setName("date");

		// Dealing with 18-59 year olds
		Table middleaged = cologneTable.where(achtzehn59);
		middleaged.sortAscendingOn("Impfdatum");
		DoubleColumn cumsumAnzahlmiddle = middleaged.intColumn("Anzahl").cumSum();
		DoubleColumn quotemiddleColumn = cumsumAnzahlmiddle.divide(676995);
		quotemiddleColumn.setName("18-59");
		middleaged.addColumns(quotemiddleColumn);
		middleaged.removeColumns("Impfschutz", "LandkreisId_Impfort", "Altersgruppe", "Anzahl");
		middleaged.column("Impfdatum").setName("date");

		//Dealing with people 60+
		Table oldpeople = cologneTable.where(sechzigplus);
		oldpeople.sortAscendingOn("Impfdatum");
		DoubleColumn cumsumAnzahlold = oldpeople.intColumn("Anzahl").cumSum() ;
		DoubleColumn quoteoldColumn = cumsumAnzahlold.divide(250986);
		quoteoldColumn.setName("60+");
		oldpeople.addColumns(quoteoldColumn);
		oldpeople.removeColumns("Impfschutz", "LandkreisId_Impfort", "Altersgruppe", "Anzahl");
		oldpeople.column("Impfdatum").setName("date");

		//Dealing with overall population
		cologneTable.sortAscendingOn("Impfdatum");
		DateColumn impfdatum = cologneTable.dateColumn("Impfdatum");
		impfdatum.sortAscending();
		DateColumn impfdatenall = DateColumn.create("date");
		IntColumn anzahlall = IntColumn.create("Anzahl");
		for (LocalDate datum : impfdatum){
			Selection thisdate = impfdatum.isEqualTo(datum);
			Table thisdateTable = cologneTable.where(thisdate);
			int sumthisdata = (int) thisdateTable.intColumn("Anzahl").sum();
			anzahlall.append(sumthisdata);
			impfdatenall.append(datum);
		}
		Table gesamtnonunique = Table.create("Quote overall", impfdatenall, anzahlall);
		Table gesamtunique = gesamtnonunique.dropDuplicateRows();
		DoubleColumn cumsumAnzahlall = gesamtunique.intColumn("Anzahl").cumSum() ;
		DoubleColumn quoteallColumn = cumsumAnzahlall.divide(1083498);
		quoteallColumn.setName("all");
		Table all = Table.create("overall population", gesamtunique.dateColumn(0), quoteallColumn);

		System.out.println(youngpeople);

	}
}
