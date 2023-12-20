package me.fallenbreath.distributary.network.handler;

import me.fallenbreath.distributary.config.Address;
import me.fallenbreath.distributary.config.Route;

class RouteResult
{
	public final Route route;
	public final Address address;

	RouteResult(Route route, Address address)
	{
		this.route = route;
		this.address = address;
	}
}
