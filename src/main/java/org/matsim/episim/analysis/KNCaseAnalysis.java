package org.matsim.episim.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;
import tech.tablesaw.api.CategoricalColumn;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.NumericColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.plotly.Plot;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;
import tech.tablesaw.plotly.traces.BarTrace;
import tech.tablesaw.plotly.traces.Trace;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.TreeMap;

class KNCaseAnalysis {
	private static final Logger log = Logger.getLogger(KNCaseAnalysis.class);

	public static void main(String[] args) throws IOException {

		Map<String, Double> anzahlFall = new TreeMap<>();

		String base = "/Users/kainagel/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/RKI/raw-data/";
		Reader in = new FileReader(base + "RKI_COVID19_13052020.csv");
		Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter(',').parse(in);
		for (CSVRecord record : records) {
			if (!record.get("Bundesland").equals("Berlin")) {
				continue;
			}
			String refDate = record.get("Refdatum");
			Double sumSoFar = anzahlFall.get(refDate);
			if (sumSoFar == null) {
				sumSoFar = 0.;
			}
			final int newCases = Integer.parseInt(record.get("AnzahlFall"));//- Integer.parseInt( record.get("AnzahlTodesfall") ) - Integer
			// .parseInt( record.get("AnzahlGenesen") );
			anzahlFall.put(refDate, sumSoFar + newCases);
		}

		CategoricalColumn<String> xColumn = StringColumn.create("date", anzahlFall.keySet());
		NumericColumn<Double> yColumn = DoubleColumn.create("AnzahlFall", anzahlFall.values());

//		Trace trace = ScatterTrace.builder( xColumn, yColumn ).name( yColumn.name() ).build();
		Trace trace = BarTrace.builder(xColumn, yColumn).build();

		Figure fig = new Figure(trace);

		Axis yAxis = Axis.builder().type(Axis.Type.LINEAR).build();
		Layout layout = Layout.builder().width(1600).height(500).yAxis(yAxis).build();
		fig.setLayout(layout);

		Plot.show(fig, "divname");


	}

}
