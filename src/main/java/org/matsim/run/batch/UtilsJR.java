package org.matsim.run.batch;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import org.matsim.episim.model.ImmunityEvent;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
import tech.tablesaw.api.DateColumn;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;
import tech.tablesaw.plotly.components.Page;
import tech.tablesaw.plotly.traces.ScatterTrace;
import tech.tablesaw.table.TableSliceGroup;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

public class UtilsJR {

	static void produceDiseaseImportPlot(Map<VirusStrain, NavigableMap<LocalDate, Integer>> infections_pers_per_day) {

		LocalDate startDate = LocalDate.of(2020, 2, 1);
		LocalDate endDate = LocalDate.of(2023, 2, 25);

		DateColumn recordsDate = DateColumn.create("date");
		DoubleColumn values = DoubleColumn.create("import");
		StringColumn groupings = StringColumn.create("scenario");
		for (VirusStrain strain : infections_pers_per_day.keySet()) {

			NavigableMap<LocalDate, Integer> mapForStrain = infections_pers_per_day.get(strain);
			int prev = 0;


			LocalDate date = startDate;
			while (date.isBefore(endDate)) {
				recordsDate.append(date);
				int val = mapForStrain.getOrDefault(date, prev);
				values.append(val);
				prev = val;
				groupings.append(strain.toString());

				date = date.plusDays(1);
			}

//			for (Map.Entry<LocalDate, Integer> entry : infections_pers_per_day.get(strain).entrySet()) {
//				recordsDate.append(entry.getKey());
//				values.append(entry.getValue());
//				groupings.append(strain.toString());
//			}
		}

		producePlot(recordsDate,values,groupings,"import by strain", "import", "importByStrain.html");



	}

	static void produceMaskPlot(Config policyConfig) {


		LocalDate startDate = LocalDate.of(2020, 2, 1);
		LocalDate endDate = LocalDate.of(2023, 2, 25);

		DateColumn recordsDate = DateColumn.create("date");
		DoubleColumn values = DoubleColumn.create("import");
		StringColumn groupings = StringColumn.create("scenario");


		for (Map.Entry<String, ConfigValue> config : policyConfig.entrySet()) {

			System.out.println(config.getKey());
			System.out.println(config.getValue());


		}
//
//			(HashMap<String,>) config.getValue()
//
//			NavigableMap<LocalDate, Integer> mapForStrain = infections_pers_per_day.get(strain);
//			int prev = 0;
//
//
//			LocalDate date = startDate;
//			while (date.isBefore(endDate)) {
//				recordsDate.append(date);
//				int val = mapForStrain.getOrDefault(date, prev);
//				values.append(val);
//				prev = val;
//				groupings.append(strain.toString());
//
//				date = date.plusDays(1);
//			}

//			for (Map.Entry<LocalDate, Integer> entry : infections_pers_per_day.get(strain).entrySet()) {
//				recordsDate.append(entry.getKey());
//				values.append(entry.getValue());
//				groupings.append(strain.toString());
//			}
		}

//		producePlot(recordsDate,values,groupings,"import by strain", "import", "importByStrain-old22.html");



//	}


	private static void producePlot(DateColumn records, DoubleColumn values, StringColumn groupings, String title, String yAxisTitle, String filename) {
		// Make plot
		Table table = Table.create(title);
		table.addColumns(records);
		table.addColumns(values);
		table.addColumns(groupings);

		TableSliceGroup tables = table.splitOn(table.categoricalColumn("scenario"));

		Axis xAxis = Axis.builder().title("Datum").build();
		Axis yAxis = Axis.builder().range(0., 20.)
				//				  .type(Axis.Type.LOG)
				.title(yAxisTitle).build();

		Layout layout = Layout.builder(title).xAxis(xAxis).yAxis(yAxis).showLegend(true).height(500).width(1000).build();

		ScatterTrace[] traces = new ScatterTrace[tables.size()];
		for (int i = 0; i < tables.size(); i++) {
			List<Table> tableList = tables.asTableList();
			traces[i] = ScatterTrace.builder(tableList.get(i).dateColumn("date"), tableList.get(i).numberColumn("import"))
					.showLegend(true)
					.name(tableList.get(i).name())
					.mode(ScatterTrace.Mode.LINE)
					.build();
		}
		var figure = new Figure(layout, traces);

		try (Writer writer = new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8)) {
			writer.write(Page.pageBuilder(figure, "target").build().asJavascript());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}


    protected static void printInitialAntibodiesToConsole(Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies) {
        System.out.print("immunityGiver");
        for (VirusStrain immunityFrom : VirusStrain.values()) {
            if (immunityFrom == VirusStrain.OMICRON_BA1) {
                System.out.print( "," + "BA.1");
            } else 		if (immunityFrom == VirusStrain.OMICRON_BA2) {
                System.out.print( "," + "BA.2");
            } else {
                System.out.print( "," + immunityFrom);
            }
        }


        for (ImmunityEvent immunityGiver : VaccinationType.values()) {
            System.out.print("\n" + immunityGiver);
            for (VirusStrain immunityFrom : VirusStrain.values()) {
                System.out.print("," +  String.format("%.3g", initialAntibodies.get(immunityGiver).get(immunityFrom)));
            }
        }
        for (ImmunityEvent immunityGiver : VirusStrain.values()) {
            System.out.print("\n" + immunityGiver);
            for (VirusStrain immunityFrom : VirusStrain.values()) {
                System.out.print("," + String.format("%.3g", initialAntibodies.get(immunityGiver).get(immunityFrom)));
            }
        }

        System.out.println();
    }
}
