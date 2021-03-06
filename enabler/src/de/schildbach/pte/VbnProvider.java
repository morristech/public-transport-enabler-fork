/*
 * Copyright 2010-2012 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.pte;

import java.io.IOException;
import java.util.List;

import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.NearbyStationsResult;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.util.ParserUtils;

/**
 * @author Andreas Schildbach
 */
public class VbnProvider extends AbstractHafasProvider
{
	public static final NetworkId NETWORK_ID = NetworkId.VBN;
	private static final String API_BASE = "http://www.fahrplaner.de/hafas/";

	public VbnProvider()
	{
		super(API_BASE + "query.exe/dn", 10, null);
	}

	public NetworkId id()
	{
		return NETWORK_ID;
	}

	public boolean hasCapabilities(Capability... capabilities)
	{
		for (final Capability capability : capabilities)
			if (capability == Capability.AUTOCOMPLETE_ONE_LINE || capability == Capability.DEPARTURES)
				return true;

		return false;
	}

	@Override
	protected void setProductBits(final StringBuilder productBits, final char product)
	{
		if (product == 'I')
		{
			productBits.setCharAt(0, '1'); // Hochgeschwindigkeitszug
			productBits.setCharAt(1, '1'); // IC/EC
			productBits.setCharAt(2, '1'); // Fernverkehrszug
		}
		else if (product == 'R')
		{
			productBits.setCharAt(3, '1'); // Regionalverkehrszug
		}
		else if (product == 'S')
		{
			productBits.setCharAt(4, '1'); // S-Bahn
		}
		else if (product == 'U')
		{
			productBits.setCharAt(7, '1'); // U-Bahn
		}
		else if (product == 'T')
		{
			productBits.setCharAt(8, '1'); // Stadtbahn
		}
		else if (product == 'B')
		{
			productBits.setCharAt(5, '1'); // Bus
		}
		else if (product == 'P')
		{
			productBits.setCharAt(9, '1'); // Anruf-Sammel-Taxi
		}
		else if (product == 'F')
		{
			productBits.setCharAt(6, '1'); // Schiff
		}
		else if (product == 'C')
		{
		}
		else
		{
			throw new IllegalArgumentException("cannot handle: " + product);
		}
	}

	public NearbyStationsResult queryNearbyStations(final Location location, final int maxDistance, final int maxStations) throws IOException
	{
		final StringBuilder uri = new StringBuilder(API_BASE);

		if (location.hasLocation())
		{
			uri.append("query.exe/dny");
			uri.append("?performLocating=2&tpl=stop2json");
			uri.append("&look_maxno=").append(maxStations != 0 ? maxStations : 200);
			uri.append("&look_maxdist=").append(maxDistance != 0 ? maxDistance : 5000);
			uri.append("&look_stopclass=").append(allProductsInt());
			uri.append("&look_x=").append(location.lon);
			uri.append("&look_y=").append(location.lat);

			return jsonNearbyStations(uri.toString());
		}
		else if (location.type == LocationType.STATION && location.hasId())
		{
			uri.append("stboard.exe/dn");
			uri.append("?productsFilter=").append(allProductsString());
			uri.append("&boardType=dep");
			uri.append("&input=").append(location.id);
			uri.append("&sTI=1&start=yes&hcount=0");
			uri.append("&L=vs_java3");

			return xmlNearbyStations(uri.toString());
		}
		else
		{
			throw new IllegalArgumentException("cannot handle: " + location.toDebugString());
		}
	}

	public QueryDeparturesResult queryDepartures(final int stationId, final int maxDepartures, final boolean equivs) throws IOException
	{
		final StringBuilder uri = new StringBuilder();
		uri.append(API_BASE).append("stboard.exe/dn");
		uri.append("?productsFilter=").append(allProductsString());
		uri.append("&boardType=dep");
		uri.append("&disableEquivs=yes"); // don't use nearby stations
		uri.append("&maxJourneys=50"); // ignore maxDepartures because result contains other stations
		uri.append("&start=yes");
		uri.append("&L=vs_java3");
		uri.append("&input=").append(stationId);

		return xmlQueryDepartures(uri.toString(), stationId);
	}

	private static final String AUTOCOMPLETE_URI = API_BASE
			+ "ajax-getstop.exe/dny?start=1&tpl=suggest2json&REQ0JourneyStopsS0A=255&REQ0JourneyStopsS0B=5&REQ0JourneyStopsB=12&getstop=1&noSession=yes&REQ0JourneyStopsS0G=%s?&js=true&";

	public List<Location> autocompleteStations(final CharSequence constraint) throws IOException
	{
		final String uri = String.format(AUTOCOMPLETE_URI, ParserUtils.urlEncode(constraint.toString(), ISO_8859_1));

		return jsonGetStops(uri);
	}

	@Override
	protected char normalizeType(final String type)
	{
		final String ucType = type.toUpperCase();

		if ("P".equals(ucType)) // Brohltalbahn
			return 'R';

		if ("TB".equals(ucType))
			return 'B';
		if ("RFTAST".equals(ucType))
			return 'B';

		if ("BUSFÄHRE".equals(ucType)) // Blexen - Bremerhaven
			return 'F';

		if ("SEILB".equals(ucType))
			return 'C';

		final char t = super.normalizeType(type);
		if (t != 0)
			return t;

		return 0;
	}
}
