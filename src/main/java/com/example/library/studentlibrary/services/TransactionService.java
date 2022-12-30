package com.example.library.studentlibrary.services;

import com.example.library.studentlibrary.models.*;
import com.example.library.studentlibrary.repositories.BookRepository;
import com.example.library.studentlibrary.repositories.CardRepository;
import com.example.library.studentlibrary.repositories.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class TransactionService {

    @Autowired
    BookRepository bookRepository5;

    @Autowired
    CardRepository cardRepository5;

    @Autowired
    TransactionRepository transactionRepository5;

    @Value("${books.max_allowed}")
    int max_allowed_books;

    @Value("${books.max_allowed_days}")
    int getMax_allowed_days;

    @Value("${books.fine.per_day}")
    int fine_per_day;

    public String issueBook(int cardId, int bookId) throws Exception {
        //check whether bookId and cardId already exist
        //conditions required for successful transaction of issue book:

        Book book = bookRepository5.findById(bookId).get();
        Card card = cardRepository5.findById(cardId).get();

        Transaction transaction1 = new Transaction();
        transaction1.setBook(book);
        transaction1.setCard(card);

        //1. book is present and available
        // If it fails: throw new Exception("Book is either unavailable or not present");
        if(book == null || !book.isAvailable()){
            transaction1.setTransactionStatus(TransactionStatus.FAILED);
            transactionRepository5.save(transaction1);
            throw new Exception("Book is either unavailable or not present");
        }
        //2. card is present and activated
        // If it fails: throw new Exception("Card is invalid");
        if(card == null || card.getCardStatus() == CardStatus.DEACTIVATED){
            transaction1.setTransactionStatus(TransactionStatus.FAILED);
            transactionRepository5.save(transaction1);
            throw new Exception("Card is invalid");
        }
        //3. number of books issued against the card is strictly less than max_allowed_books
        // If it fails: throw new Exception("Book limit has reached for this card");
        if(card.getBooks().size() >= max_allowed_books){
            transaction1.setTransactionStatus(TransactionStatus.FAILED);
            transactionRepository5.save(transaction1);
            throw new Exception("Book limit has reached for this card");
        }

        List <Book> cardBooks = card.getBooks();
        cardBooks.add(book);
        card.setBooks(cardBooks);

        book.setCard(card);
        book.setAvailable(false);
        bookRepository5.updateBook(book);

        //If the transaction is successful, save the transaction to the list of transactions and return the id
        transaction1.setTransactionStatus(TransactionStatus.SUCCESSFUL);
        transactionRepository5.save(transaction1);

       return transaction1.getTransactionId(); //return transactionId instead
    }

    public Transaction returnBook(int cardId, int bookId) throws Exception{

        List<Transaction> transactions = transactionRepository5.find(cardId, bookId,TransactionStatus.SUCCESSFUL, true);
        Transaction transaction = transactions.get(transactions.size() - 1);

        //for the given transaction calculate the fine amount considering the book has been returned exactly when this function is called
        int fine = 0;
        Date transactionDate = transaction.getTransactionDate();
        long transDateInMilliSec = transactionDate.getTime();

        long currentDateInMilliSec = System.currentTimeMillis();

        long noOfDaysAfterIssue = currentDateInMilliSec - transDateInMilliSec;
        if(noOfDaysAfterIssue > getMax_allowed_days){
            fine += (noOfDaysAfterIssue - getMax_allowed_days)*fine_per_day;
        }

        //make the book available for other users
        Book book = transaction.getBook();
        book.setAvailable(true);
        book.setCard(null);
        bookRepository5.save(book);

        //make a new transaction for return book which contains the fine amount as well
        Transaction returnBookTransaction  = new Transaction();
        returnBookTransaction.setTransactionStatus(TransactionStatus.SUCCESSFUL);
        returnBookTransaction.setFineAmount(fine);
        returnBookTransaction.setIssueOperation(false);
        returnBookTransaction.setBook(book);
        returnBookTransaction.setCard(transaction.getCard());
        transactionRepository5.save(returnBookTransaction);

        return returnBookTransaction; //return the transaction after updating all details
    }
}