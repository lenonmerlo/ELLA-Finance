package com.ella.backend.services.bankstatements.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ella.backend.entities.BankStatementTransaction;
import com.ella.backend.services.bankstatements.parsers.ItauBankStatementParser.ParsedBankStatement;
import com.ella.backend.services.bankstatements.parsers.ItauBankStatementParser.ParsedTransaction;

@DisplayName("ItauBankStatementParser - Testes de Extrato Bancário")
public class ItauBankStatementParserTest {

    private ItauBankStatementParser parser;

    @BeforeEach
    void setUp() {
        parser = new ItauBankStatementParser();
    }

    @Test
    void testIsBalanceLine_DetectsSaldoAnterior() {
        assertTrue(ItauBankStatementParser.isBalanceLine("SALDO ANTERIOR"));
        assertTrue(ItauBankStatementParser.isBalanceLine("SALDO TOTAL DISPONÍVEL DIA"));
        assertTrue(ItauBankStatementParser.isBalanceLine("SALDO TOTAL DISPONÍVEL"));

        assertFalse(ItauBankStatementParser.isBalanceLine("PIX TRANSF GILDA"));
        assertFalse(ItauBankStatementParser.isBalanceLine("PAG BOLETO"));
    }

    @Test
    void testExtractTransactions_IgnoresBalanceLines() {
        String text = String.join("\n",
                "30/11/2025 SALDO ANTERIOR                           1.609,27",
                "01/12/2025 PIX TRANSF GILDA D01/12      325,00D     1.284,27",
                "02/12/2025 SALDO TOTAL DISPONÍVEL DIA               1.284,27"
        );

        ParsedBankStatement parsed = parser.parse(text);
        List<ParsedTransaction> transactions = parsed.getTransactions();

        long realTransactions = transactions.stream()
                .filter(t -> t.type() != BankStatementTransaction.Type.BALANCE)
                .count();

        assertEquals(1, realTransactions);

        ParsedTransaction tx = transactions.stream()
                .filter(t -> t.type() != BankStatementTransaction.Type.BALANCE)
                .findFirst()
                .orElseThrow();

        assertEquals(LocalDate.of(2025, 12, 1), tx.transactionDate());
        assertEquals("PIX TRANSF GILDA D01/12", tx.description());
        assertEquals(new BigDecimal("-325.00"), tx.amount());
        assertEquals(BankStatementTransaction.Type.DEBIT, tx.type());
    }

    @Test
    void testExtractTransactions_SaldoAnteriorHasZeroAmount() {
        String text = String.join("\n",
                "30/11/2025 SALDO ANTERIOR                           1.609,27"
        );

        ParsedBankStatement parsed = parser.parse(text);
        List<ParsedTransaction> transactions = parsed.getTransactions();

        assertEquals(1, transactions.size());

        ParsedTransaction tx = transactions.get(0);
        assertEquals("SALDO ANTERIOR", tx.description());
        assertEquals(0, tx.amount().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("1609.27"), tx.balance());
        assertEquals(BankStatementTransaction.Type.BALANCE, tx.type());
    }

    @Test
    void testExtractTransactions_MultipleBalanceLines() {
        String text = String.join("\n",
                "30/11/2025 SALDO ANTERIOR                           1.609,27",
                "01/12/2025 PIX TRANSF GILDA D01/12      325,00D     1.284,27",
                "01/12/2025 SALDO TOTAL DISPONÍVEL DIA               1.284,27",
                "02/12/2025 IOF                           34,67D      1.249,60",
                "02/12/2025 SALDO TOTAL DISPONÍVEL DIA               1.249,60"
        );

        List<ParsedTransaction> transactions = parser.parse(text).getTransactions();

        assertEquals(5, transactions.size());

        assertEquals(BankStatementTransaction.Type.BALANCE, transactions.get(0).type());
        assertEquals(BankStatementTransaction.Type.DEBIT, transactions.get(1).type());
        assertEquals(BankStatementTransaction.Type.BALANCE, transactions.get(2).type());
        assertEquals(BankStatementTransaction.Type.DEBIT, transactions.get(3).type());
        assertEquals(BankStatementTransaction.Type.BALANCE, transactions.get(4).type());
    }

    @Test
    void testCalculateTotals_IgnoresBalanceLines() {
        String text = String.join("\n",
                "30/11/2025 SALDO ANTERIOR                           1.609,27",
                "01/12/2025 PIX TRANSF GILDA D01/12      325,00D     1.284,27",
                "01/12/2025 SALDO TOTAL DISPONÍVEL DIA               1.284,27",
                "02/12/2025 IOF                           34,67D      1.249,60",
                "22/12/2025 PIX TRANSF JULIA C20/12       20,00C      1.269,60"
        );

        List<ParsedTransaction> transactions = parser.parse(text).getTransactions();

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;

        for (ParsedTransaction tx : transactions) {
            if (tx.type() == BankStatementTransaction.Type.BALANCE) {
                continue;
            }
            if (tx.type() == BankStatementTransaction.Type.CREDIT) {
                totalIncome = totalIncome.add(tx.amount());
            } else if (tx.type() == BankStatementTransaction.Type.DEBIT) {
                totalExpenses = totalExpenses.add(tx.amount().abs());
            }
        }

        assertEquals(new BigDecimal("20.00"), totalIncome);
        assertEquals(new BigDecimal("359.67"), totalExpenses);
    }

    @Test
    void testParseRealItauStatement() {
        String text = String.join("\n",
                "30/11/2025 SALDO ANTERIOR                           1.609,27",
                "01/12/2025 PIX TRANSF GILDA D01/12      325,00D     1.284,27",
                "01/12/2025 SALDO TOTAL DISPONÍVEL DIA               1.284,27",
                "02/12/2025 IOF                           34,67D      1.249,60",
                "02/12/2025 SALDO TOTAL DISPONÍVEL DIA               1.249,60",
                "03/12/2025 PAG BOLETO SOCIEDADE ED      225,50D     1.024,10",
                "03/12/2025 PAG BOLETO F I A P           720,00D       304,10",
                "03/12/2025 PIX QRS GAMA FISIO03/12      200,00D       104,10",
                "03/12/2025 SALDO TOTAL DISPONÍVEL DIA                104,10"
        );

        ParsedBankStatement parsed = parser.parse(text);
        List<ParsedTransaction> transactions = parsed.getTransactions();

        long realTransactions = transactions.stream()
                .filter(t -> t.type() != BankStatementTransaction.Type.BALANCE)
                .count();

        assertEquals(5, realTransactions);

        BigDecimal totalExpenses = transactions.stream()
                .filter(t -> t.type() == BankStatementTransaction.Type.DEBIT)
                .map(ParsedTransaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .abs();

        assertEquals(new BigDecimal("1505.17"), totalExpenses);
    }

    @Test
    void testExtractOpeningBalance_FromSaldoAnterior() {
        String text = String.join("\n",
                "30/11/2025 SALDO ANTERIOR                           1.609,27",
                "01/12/2025 PIX TRANSF GILDA D01/12      325,00D     1.284,27"
        );

        ParsedBankStatement parsed = parser.parse(text);
        assertEquals(new BigDecimal("1609.27"), parsed.getOpeningBalance());
    }

    @Test
    void testExtractClosingBalance_FromLastTransaction() {
        String text = String.join("\n",
                "30/11/2025 SALDO ANTERIOR                           1.609,27",
                "01/12/2025 PIX TRANSF GILDA D01/12      325,00D     1.284,27",
                "31/12/2025 PIX TRANSF AFONSO 31/12      509,99C      -943,49",
                "31/12/2025 SALDO TOTAL DISPONÍVEL DIA               -943,49"
        );

        ParsedBankStatement parsed = parser.parse(text);
        assertEquals(new BigDecimal("-943.49"), parsed.getClosingBalance());
    }

    @Test
    void testParse_WhenExtractedTextHasNoNewlines_StillExtractsTransactions() {
        // Some PDFBox extractions (especially sorted/positional) can collapse line breaks.
        // Ensure we still split entries and detect transactions.
        String singleLine = String.join(" ",
                "30/11/2025 SALDO ANTERIOR                           1.609,27",
                "01/12/2025 PIX TRANSF GILDA D01/12      325,00D     1.284,27",
                "02/12/2025 IOF                           34,67D      1.249,60",
                "02/12/2025 SALDO TOTAL DISPONÍVEL DIA               1.249,60"
        );

        ParsedBankStatement parsed = parser.parse(singleLine);
        List<ParsedTransaction> transactions = parsed.getTransactions();

        long realTransactions = transactions.stream()
                .filter(t -> t.type() != BankStatementTransaction.Type.BALANCE)
                .count();

        assertEquals(2, realTransactions);
    }
}
