package org.jnode.httpd.routes.get;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import org.jnode.httpd.dto.EchoareaCSV;

import com.j256.ormlite.dao.GenericRawResults;

import jnode.dto.Echoarea;
import jnode.logger.Logger;
import jnode.orm.ORMManager;
import io.javalin.http.Context;
import io.javalin.http.Handler;

public class EchoareaCSVRoute implements Handler {
	private static final long MAX_CACHE_TIME = 3600000;
	private long latest = 0;

	@Override
	public void handle(Context ctx) throws Exception {
		ctx.contentType("text/plain; charset=utf-8");
		long now = new Date().getTime();
		StringBuilder sb = new StringBuilder();
		if (now - latest > MAX_CACHE_TIME) {
			try {
				ORMManager.get(EchoareaCSV.class).executeRaw(
						"DELETE FROM httpd_echoarea_csv;");
				GenericRawResults<String[]> results = ORMManager
						.get(Echoarea.class)
						.getRaw("SELECT e.name,e.description,(SELECT count(id) FROM echomail "
								+ "WHERE echoarea_id=e.id) AS num,(SELECT max(date) FROM echomail "
								+ "WHERE echoarea_id=e.id) AS latest FROM echoarea e ORDER BY e.name;");
				latest = now;
				for (String[] row : results.getResults()) {
					EchoareaCSV csv = new EchoareaCSV();
					csv.setName(row[0]);
					csv.setDescription(row[1]);
					csv.setNum(new Long(row[2]));
					csv.setLatest(new Long(row[3])/1000L);
					ORMManager.get(EchoareaCSV.class).save(csv);
					sb.append(csv.getName() + "," + csv.getLatest() + ","
							+ csv.getNum() + "," + csv.getDescription()
							+ "\r\n");
				}
			} catch (SQLException e) {
				Logger.getLogger(EchoareaCSVRoute.class)
						.l1("Echoarea Error", e);
				ctx.result("error,0,0,SQLError\r\n");
				return;
			}
		} else {
			List<EchoareaCSV> list = ORMManager.get(EchoareaCSV.class)
					.getOrderAnd("name", true);
			for (EchoareaCSV csv : list) {
				sb.append(csv.getName() + "," + csv.getLatest() + ","
						+ csv.getNum() + "," + csv.getDescription() + "\r\n");
			}
		}
		ctx.result(sb.toString());
	}
}
