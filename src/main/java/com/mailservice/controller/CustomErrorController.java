package com.mailservice.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * ErrorController personalizado que renderiza error.html para rutas admin
 * y devuelve JSON para la API REST.
 */
@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object error = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);

        int statusCode = status != null ? Integer.parseInt(status.toString()) : 500;
        String errorMsg = error != null && !error.toString().isEmpty()
                ? error.toString()
                : getDefaultMessage(statusCode);

        model.addAttribute("status", statusCode);
        model.addAttribute("error", getErrorTitle(statusCode));
        model.addAttribute("message", errorMsg);

        return "error";
    }

    private String getErrorTitle(int status) {
        return switch (status) {
            case 400 -> "Solicitud Inválida";
            case 403 -> "Acceso Denegado";
            case 404 -> "Página No Encontrada";
            case 405 -> "Método No Permitido";
            case 500 -> "Error Interno";
            default -> "Error " + status;
        };
    }

    private String getDefaultMessage(int status) {
        return switch (status) {
            case 404 -> "La página que buscas no existe o fue movida.";
            case 403 -> "No tienes permisos para acceder a esta página.";
            case 500 -> "Ocurrió un error interno. Intenta de nuevo más tarde.";
            default -> "Algo salió mal.";
        };
    }
}
