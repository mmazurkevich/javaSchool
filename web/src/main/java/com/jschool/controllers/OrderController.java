package com.jschool.controllers;

import com.jschool.AppContext;
import com.jschool.Validator;
import com.jschool.controllers.exception.ControllerException;
import com.jschool.entities.*;
import com.jschool.services.api.DriverService;
import com.jschool.services.api.OrderAndCargoService;
import com.jschool.services.api.TruckService;
import com.jschool.services.api.exception.ServiceException;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by infinity on 12.02.16.
 */
public class OrderController implements Command {

    private AppContext appContext = AppContext.getInstance();
    private OrderAndCargoService orderAndCargoService = appContext.getOrderAndCargoService();
    private TruckService truckService = appContext.getTruckService();
    private DriverService driverService = appContext.getDriverService();
    private Validator validator = appContext.getValidator();

    public void execute(ServletContext servletContext, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String[] uri = request.getRequestURI().split("/");
        if (request.getMethod().equals("GET")) {
            if (uri.length == 4 && uri[3].equals("orders"))
                showOrders(request, response);
            else if (uri.length == 5 && uri[4].equals("add"))
                showFormForOrderAdd(request, response);
        }
        if (request.getMethod().equals("POST")) {
            if (uri.length == 5 && uri[4].equals("add"))
                addOrder(request, response);
            else if (uri.length == 5 && uri[4].equals("submit"))
                submitOrder(request, response);
        }
    }

    // /employee/orders/
    public void showOrders(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            List<Order> orders = orderAndCargoService.findAllOrders();
            Map<Order, List<Cargo>> orderListMap = new HashMap<>();
            for (Order order : orders) {
                List<Cargo> cargos = orderAndCargoService.findAllCargosByOrderNumber(order.getNumber());
                orderListMap.put(order, cargos);
            }
            req.setAttribute("orderListMap", orderListMap);
            req.getRequestDispatcher("/WEB-INF/pages/order/order.jsp").forward(req, resp);
        } catch (ServiceException e) {
            req.setAttribute("error",e);
            req.getRequestDispatcher("/WEB-INF/pages/error.jsp").forward(req, resp);
        }
    }

    // /employee/order/add GET
    public void showFormForOrderAdd(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/pages/order/orderAllAdd.jsp").forward(req, resp);
    }

    //   /employee/order/add POST
    public void addOrder(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String stepNumber = req.getParameter("step_number");
            switch (stepNumber) {
                case "1":
                    req.getRequestDispatcher("/WEB-INF/pages/order/orderCargo.jsp").forward(req, resp);
                    break;
                case "2":
                    String cargoWeight[] = req.getParameterValues("cargoWeight");
                    validator.validateCargoWeight(cargoWeight);
                    int max = 0;
                    for (String weight : cargoWeight) {
                        if (Integer.parseInt(weight) > max)
                            max = Integer.parseInt(weight);
                    }
                    List<Truck> trucks = truckService.findAllAvailableTrucksByMinCapacity(max);
                    req.setAttribute("trucks", trucks);
                    req.setAttribute("max", max);
                    req.getRequestDispatcher("/WEB-INF/pages/order/orderTruck.jsp").forward(req, resp);
                    break;
                case "3":
                    String pickup[] = req.getParameterValues("pickup");
                    String unload[] = req.getParameterValues("unload");
                    validator.validateCargoCities(pickup,unload);
                    List<String> cities = new ArrayList<>();
                    for (int i = 0; i < pickup.length; i++) {
                        cities.add(pickup[i]);
                        cities.add(unload[i]);
                    }
                    req.setAttribute("cities", cities);
                    req.getRequestDispatcher("/WEB-INF/pages/order/orderMap.jsp").forward(req, resp);
                    break;
                case "4":
                    String duration = req.getParameter("duration").split(" ")[0];
                    String truckNumber = req.getParameter("truckNumber");
                    validator.validateTruckNumber(truckNumber);
                    Truck truck = truckService.getTruckByNumber(truckNumber);
                    Map<Driver, Integer> driverHoursList = driverService.findAllAvailableDrivers(Integer.parseInt(duration));
                    req.setAttribute("drivers", driverHoursList);
                    req.setAttribute("duration", duration);
                    req.setAttribute("shiftSize", truck.getShiftSize());
                    req.getRequestDispatcher("/WEB-INF/pages/order/orderDrivers.jsp").forward(req, resp);
                    break;
            }
        } catch (ServiceException | ControllerException e) {
            req.setAttribute("error",e);
            req.getRequestDispatcher("/WEB-INF/pages/error.jsp").forward(req, resp);
        }
    }

    // /employee/order/submit POST
    public void submitOrder(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            String orderNumber = req.getParameter("orderNumber");
            String[] cargoNumber = req.getParameterValues("cargoNumber");
            String[] cargoName = req.getParameterValues("cargoName");
            String[] cargoWeight = req.getParameterValues("cargoWeight");
            String[] pickup = req.getParameterValues("pickup");
            String[] unload = req.getParameterValues("unload");
            String truckNumber = req.getParameter("truckNumber");
            String[] driverNumbers = req.getParameterValues("driverNumber");
            validator.validateOrderAndCargo(orderNumber,cargoNumber,cargoName,cargoWeight,pickup,unload);
            validator.validateTruckAndDrivers(truckNumber,driverNumbers);

            Order order = new Order();
            order.setNumber(Integer.parseInt(orderNumber));
            order.setDoneState(false);
            List<Cargo> cargos = new ArrayList<>();
            for (int i = 0; i < cargoNumber.length; i++) {
                Cargo cargo = new Cargo();
                cargo.setNumber(Integer.parseInt(cargoNumber[i]));
                cargo.setName(cargoName[i]);
                cargo.setWeight(Integer.parseInt(cargoWeight[i]));
                City pickCity = new City();
                pickCity.setName(pickup[i]);
                City unloadCity = new City();
                unloadCity.setName(unload[i]);
                RoutePoint pickRoute = new RoutePoint();
                pickRoute.setPoint(i);
                pickRoute.setCity(pickCity);
                RoutePoint unloadRoute = new RoutePoint();
                unloadRoute.setPoint(i);
                unloadRoute.setCity(unloadCity);
                cargo.setPickup(pickRoute);
                cargo.setUnload(unloadRoute);
                cargos.add(cargo);
            }
            Truck truck = truckService.getTruckByNumber(truckNumber);
            order.setTruck(truck);
            List<Driver> drivers = new ArrayList<>();
            for (String driver : driverNumbers)
                drivers.add(driverService.getDriverByPersonalNumber(Integer.parseInt(driver)));
            order.setTruck(truck);
            order.setDrivers(drivers);
            orderAndCargoService.addOrder(order,cargos);
            resp.sendRedirect("/employee/orders");
        }catch (ServiceException | ControllerException e){
            req.setAttribute("error",e);
            req.getRequestDispatcher("/WEB-INF/pages/error.jsp").forward(req, resp);
        }
    }

}
