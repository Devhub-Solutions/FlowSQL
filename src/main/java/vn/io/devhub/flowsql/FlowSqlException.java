package vn.io.devhub.flowsql;

/** Loi runtime khi thuc thi SQL bang API "strict" ({@code query}, {@code execute}, {@code insert}...). */
public class FlowSqlException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public FlowSqlException(String message, Throwable cause) {
        super(message, cause);
    }

    public FlowSqlException(String message) {
        super(message);
    }
}
